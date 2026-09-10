package br.com.lojagenerica.core.venda;

import br.com.lojagenerica.core.acesso.Papel;
import br.com.lojagenerica.core.acesso.Usuario;
import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.core.auditoria.AuditoriaService;
import br.com.lojagenerica.core.auditoria.EventoAuditoria;
import br.com.lojagenerica.core.cadastro.FormaPagamento;
import br.com.lojagenerica.core.cadastro.FormaPagamentoRepository;
import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.cadastro.LocalEstoqueRepository;
import br.com.lojagenerica.core.cadastro.SentidoMovimentacao;
import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
import br.com.lojagenerica.core.estoque.MovimentacaoEstoqueService;
import br.com.lojagenerica.core.estoque.OrigemMovimentacao;
import br.com.lojagenerica.core.estoque.RegistrarMovimentacaoCommand;
import br.com.lojagenerica.core.parceiro.Cliente;
import br.com.lojagenerica.core.parceiro.ClienteRepository;
import br.com.lojagenerica.core.produto.Produto;
import br.com.lojagenerica.core.produto.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link #registrar} monta e confirma a venda numa chamada só (diferente de
 * {@code CompraService}, que mantém rascunho editável por várias
 * requisições) — é assim que um PDV/checkout real funciona: não faz
 * sentido uma venda "meio pronta" ficando pendurada. Idempotente pelo
 * {@code uuid} (gerado no cliente/terminal, ver Fase D) — como o método
 * inteiro é uma única transação, uma falha no meio do caminho desfaz tudo
 * (nunca deixa uma venda "confirmada pela metade"); uma segunda tentativa
 * com o mesmo uuid encontra a venda já commitada e a devolve sem repetir
 * nada.
 */
@Service
public class VendaService {

    private final VendaRepository vendaRepository;
    private final ClienteRepository clienteRepository;
    private final LocalEstoqueRepository localEstoqueRepository;
    private final ProdutoRepository produtoRepository;
    private final UnidadeMedidaRepository unidadeMedidaRepository;
    private final FormaPagamentoRepository formaPagamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimentacaoEstoqueService movimentacaoEstoqueService;
    private final AuditoriaService auditoriaService;

    public VendaService(VendaRepository vendaRepository, ClienteRepository clienteRepository,
                         LocalEstoqueRepository localEstoqueRepository, ProdutoRepository produtoRepository,
                         UnidadeMedidaRepository unidadeMedidaRepository,
                         FormaPagamentoRepository formaPagamentoRepository, UsuarioRepository usuarioRepository,
                         MovimentacaoEstoqueService movimentacaoEstoqueService, AuditoriaService auditoriaService) {
        this.vendaRepository = vendaRepository;
        this.clienteRepository = clienteRepository;
        this.localEstoqueRepository = localEstoqueRepository;
        this.produtoRepository = produtoRepository;
        this.unidadeMedidaRepository = unidadeMedidaRepository;
        this.formaPagamentoRepository = formaPagamentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimentacaoEstoqueService = movimentacaoEstoqueService;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public Venda registrar(RegistrarVendaCommand cmd) {
        if (cmd.uuid() != null) {
            var existente = vendaRepository.findByUuid(cmd.uuid());
            if (existente.isPresent()) {
                Venda venda = existente.get();
                // Mesma armadilha de CompraService.confirmar(): força a
                // inicialização das coleções lazy AQUI, com a sessão ainda
                // aberta, senão o controller quebra (LazyInitializationException)
                // ao serializar a resposta fora da transação.
                venda.getItens().size();
                venda.getPagamentos().size();
                return venda;
            }
        }
        if (cmd.itens() == null || cmd.itens().isEmpty()) {
            throw new IllegalArgumentException("Venda precisa de ao menos 1 item");
        }

        LocalEstoque local = localEstoqueRepository.findById(cmd.localEstoqueId())
                .orElseThrow(() -> new NoSuchElementException("Local de estoque " + cmd.localEstoqueId() + " não encontrado"));
        Cliente cliente = cmd.clienteId() != null
                ? clienteRepository.findById(cmd.clienteId())
                        .orElseThrow(() -> new NoSuchElementException("Cliente " + cmd.clienteId() + " não encontrado"))
                : null;

        Venda venda = new Venda(cmd.canal(), local, cliente, cmd.usuarioId());
        venda.usarUuid(cmd.uuid());

        for (RegistrarVendaCommand.ItemVendaCommand itemCmd : cmd.itens()) {
            Produto produto = produtoRepository.findById(itemCmd.produtoId())
                    .orElseThrow(() -> new NoSuchElementException("Produto " + itemCmd.produtoId() + " não encontrado"));
            UnidadeMedida unidade = unidadeMedidaRepository.findById(itemCmd.unidadeId())
                    .orElseThrow(() -> new NoSuchElementException("Unidade " + itemCmd.unidadeId() + " não encontrada"));
            BigDecimal fator = movimentacaoEstoqueService.resolverFatorConversao(produto, unidade);
            BigDecimal quantidadeBase = itemCmd.quantidade().multiply(fator);

            venda.adicionarItem(new ItemVenda(produto, itemCmd.quantidade(), unidade, fator, quantidadeBase,
                    itemCmd.precoUnitario(), itemCmd.descontoValor(), produto.getCustoAquisicao()));
        }
        venda.recalcularTotais();

        validarDesconto(cmd.descontoValor(), venda.getSubtotal(), cmd.usuarioEmail());
        venda.aplicarDesconto(cmd.descontoValor());
        venda.recalcularTotais();

        if (cmd.pagamentos() != null) {
            for (RegistrarVendaCommand.PagamentoVendaCommand pagCmd : cmd.pagamentos()) {
                FormaPagamento forma = formaPagamentoRepository.findById(pagCmd.formaPagamentoId())
                        .orElseThrow(() -> new NoSuchElementException("Forma de pagamento " + pagCmd.formaPagamentoId() + " não encontrada"));
                venda.adicionarPagamento(new VendaPagamento(forma, pagCmd.valor(), pagCmd.valorRecebido(), pagCmd.troco()));
            }
        }

        venda = vendaRepository.save(venda);

        for (ItemVenda item : venda.getItens()) {
            movimentacaoEstoqueService.registrar(new RegistrarMovimentacaoCommand(
                    item.getProduto().getId(), local.getId(), "VENDA", SentidoMovimentacao.SAIDA,
                    item.getQuantidade(), item.getUnidade().getId(), null,
                    OrigemMovimentacao.VENDA, venda.getId(), item.getId(), cmd.terminalId(), cmd.usuarioId(), null));
        }

        venda.marcarConfirmada();
        return vendaRepository.save(venda);
    }

    /**
     * Idempotente: cancelar uma venda já cancelada é no-op. Reverte estoque
     * via movimentação de entrada (origem DEVOLUCAO, não colide com o
     * índice único da movimentação de saída original — ver
     * MovimentacaoEstoqueService).
     */
    @Transactional
    public Venda cancelar(Long vendaId, String motivo, Long usuarioId) {
        Venda venda = vendaRepository.findByIdComItensEPagamentos(vendaId)
                .orElseThrow(() -> new NoSuchElementException("Venda " + vendaId + " não encontrada"));
        if (venda.getStatus() == StatusVenda.CANCELADA) {
            return venda;
        }
        if (venda.getStatus() != StatusVenda.CONFIRMADA) {
            throw new IllegalStateException("Venda " + vendaId + " não está confirmada (status atual: " + venda.getStatus() + ")");
        }

        for (ItemVenda item : venda.getItens()) {
            movimentacaoEstoqueService.registrar(new RegistrarMovimentacaoCommand(
                    item.getProduto().getId(), venda.getLocalEstoque().getId(), "DEVOLUCAO_CLIENTE",
                    SentidoMovimentacao.ENTRADA, item.getQuantidade(), item.getUnidade().getId(), null,
                    OrigemMovimentacao.DEVOLUCAO, venda.getId(), item.getId(), null, usuarioId, motivo));
        }

        venda.cancelar(motivo);
        venda = vendaRepository.save(venda);

        auditoriaService.registrar(EventoAuditoria.de("VENDA_CANCELADA", "venda", venda.getId(),
                Map.of("status", StatusVenda.CONFIRMADA.name()),
                Map.of("status", StatusVenda.CANCELADA.name()), motivo));

        return venda;
    }

    /**
     * ADMINISTRADOR (papel de sistema) sempre pode; qualquer outro papel
     * precisa de {@code papel_restricao["desconto.percentual_maximo"]} —
     * sem essa restrição configurada, o default é zero (nenhum desconto),
     * não "sem limite". É assim que "desconto conforme permissão" funciona
     * sem número fixo no código (ver docs/CONTEXTO.md).
     */
    private void validarDesconto(BigDecimal descontoValor, BigDecimal subtotal, String usuarioEmail) {
        if (descontoValor == null || descontoValor.signum() <= 0) {
            return;
        }
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(Objects.requireNonNullElse(usuarioEmail, ""))
                .orElseThrow(() -> new AccessDeniedException("Usuário não identificado pra aplicar desconto"));

        boolean administrador = usuario.getPapeis().stream().anyMatch(Papel::isSistema);
        if (administrador) {
            return;
        }

        BigDecimal percentualSolicitado = subtotal.signum() == 0 ? BigDecimal.ZERO
                : descontoValor.divide(subtotal, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        BigDecimal maiorLimite = usuario.getPapeis().stream()
                .map(p -> p.getRestricoes().get("desconto.percentual_maximo"))
                .filter(Objects::nonNull)
                .map(BigDecimal::new)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        if (percentualSolicitado.compareTo(maiorLimite) > 0) {
            throw new AccessDeniedException("Desconto de " + percentualSolicitado.setScale(2, RoundingMode.HALF_UP)
                    + "% excede o limite do seu papel (" + maiorLimite + "%)");
        }
    }
}
