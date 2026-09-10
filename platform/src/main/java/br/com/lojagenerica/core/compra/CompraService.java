package br.com.lojagenerica.core.compra;

import br.com.lojagenerica.core.auditoria.AuditoriaService;
import br.com.lojagenerica.core.auditoria.EventoAuditoria;
import br.com.lojagenerica.core.cadastro.CondicaoPagamentoRepository;
import br.com.lojagenerica.core.cadastro.FormaPagamentoRepository;
import br.com.lojagenerica.core.cadastro.LocalEstoqueRepository;
import br.com.lojagenerica.core.cadastro.SentidoMovimentacao;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
import br.com.lojagenerica.core.estoque.MovimentacaoEstoqueService;
import br.com.lojagenerica.core.estoque.OrigemMovimentacao;
import br.com.lojagenerica.core.estoque.RegistrarMovimentacaoCommand;
import br.com.lojagenerica.core.parceiro.FornecedorRepository;
import br.com.lojagenerica.core.produto.Produto;
import br.com.lojagenerica.core.produto.ProdutoRepository;
import br.com.lojagenerica.core.produto.ProdutoService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link #confirmar} é o template de toda transação cross-módulo do
 * sistema: ledger de estoque + custo do produto + (Fase E) conta a pagar +
 * auditoria, numa chamada só, idempotente pelo status da compra + pelo
 * índice único do ledger (ver MovimentacaoEstoqueService).
 */
@Service
public class CompraService {

    private final CompraRepository compraRepository;
    private final FornecedorRepository fornecedorRepository;
    private final LocalEstoqueRepository localEstoqueRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoService produtoService;
    private final UnidadeMedidaRepository unidadeMedidaRepository;
    private final CondicaoPagamentoRepository condicaoPagamentoRepository;
    private final FormaPagamentoRepository formaPagamentoRepository;
    private final MovimentacaoEstoqueService movimentacaoEstoqueService;
    private final GeradorContaPagar geradorContaPagar;
    private final AuditoriaService auditoriaService;

    public CompraService(CompraRepository compraRepository, FornecedorRepository fornecedorRepository,
                          LocalEstoqueRepository localEstoqueRepository, ProdutoRepository produtoRepository,
                          ProdutoService produtoService, UnidadeMedidaRepository unidadeMedidaRepository,
                          CondicaoPagamentoRepository condicaoPagamentoRepository,
                          FormaPagamentoRepository formaPagamentoRepository,
                          MovimentacaoEstoqueService movimentacaoEstoqueService,
                          GeradorContaPagar geradorContaPagar, AuditoriaService auditoriaService) {
        this.compraRepository = compraRepository;
        this.fornecedorRepository = fornecedorRepository;
        this.localEstoqueRepository = localEstoqueRepository;
        this.produtoRepository = produtoRepository;
        this.produtoService = produtoService;
        this.unidadeMedidaRepository = unidadeMedidaRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.formaPagamentoRepository = formaPagamentoRepository;
        this.movimentacaoEstoqueService = movimentacaoEstoqueService;
        this.geradorContaPagar = geradorContaPagar;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public Compra criar(Long fornecedorId, Long localEstoqueId, Long usuarioId) {
        var fornecedor = fornecedorRepository.findById(fornecedorId)
                .orElseThrow(() -> new NoSuchElementException("Fornecedor " + fornecedorId + " não encontrado"));
        var local = localEstoqueRepository.findById(localEstoqueId)
                .orElseThrow(() -> new NoSuchElementException("Local de estoque " + localEstoqueId + " não encontrado"));
        return compraRepository.save(new Compra(fornecedor, local, usuarioId));
    }

    @Transactional
    public Compra adicionarItem(Long compraId, Long produtoId, BigDecimal quantidade, Long unidadeId,
                                 BigDecimal precoUnitario, BigDecimal descontoValor) {
        Compra compra = buscarRascunho(compraId);
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new NoSuchElementException("Produto " + produtoId + " não encontrado"));
        var unidade = unidadeMedidaRepository.findById(unidadeId)
                .orElseThrow(() -> new NoSuchElementException("Unidade " + unidadeId + " não encontrada"));

        BigDecimal fator = movimentacaoEstoqueService.resolverFatorConversao(produto, unidade);
        BigDecimal quantidadeBase = quantidade.multiply(fator);

        compra.adicionarItem(new ItemCompra(produto, quantidade, unidade, fator, quantidadeBase,
                precoUnitario, descontoValor != null ? descontoValor : BigDecimal.ZERO));
        compra.recalcularTotais();
        return compraRepository.save(compra);
    }

    @Transactional
    public Compra definirCondicoes(Long compraId, Long condicaoPagamentoId, Long formaPagamentoId,
                                    BigDecimal frete, BigDecimal outrosCustos, BigDecimal descontoValor) {
        Compra compra = buscarRascunho(compraId);
        var condicao = condicaoPagamentoId != null ? condicaoPagamentoRepository.findById(condicaoPagamentoId)
                .orElseThrow(() -> new NoSuchElementException("Condição de pagamento " + condicaoPagamentoId + " não encontrada")) : null;
        var forma = formaPagamentoId != null ? formaPagamentoRepository.findById(formaPagamentoId)
                .orElseThrow(() -> new NoSuchElementException("Forma de pagamento " + formaPagamentoId + " não encontrada")) : null;
        compra.definirCondicoes(condicao, forma, frete, outrosCustos, descontoValor);
        compra.recalcularTotais();
        return compraRepository.save(compra);
    }

    /**
     * Idempotente: confirmar uma compra que já não está mais RASCUNHO é
     * um no-op (devolve a compra como está) — nunca gera ledger/custo
     * duas vezes.
     */
    @Transactional
    public Compra confirmar(Long compraId) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new NoSuchElementException("Compra " + compraId + " não encontrada"));
        // Força a coleção lazy a inicializar AQUI, com a sessão ainda aberta —
        // sem isso, o caminho idempotente abaixo (compra já confirmada) devolve
        // uma Compra cujo getItens() só quebraria (LazyInitializationException)
        // quando o controller tentasse serializar a resposta, já fora da
        // transação (open-in-view=false, ver application.yml).
        compra.getItens().size();
        if (compra.getStatus() != StatusCompra.RASCUNHO) {
            return compra;
        }
        if (compra.getItens().isEmpty()) {
            throw new IllegalStateException("Compra " + compraId + " não tem itens");
        }

        compra.recalcularTotais();

        for (ItemCompra item : compra.getItens()) {
            BigDecimal custoUnitario = item.custoUnitarioFinal();

            movimentacaoEstoqueService.registrar(new RegistrarMovimentacaoCommand(
                    item.getProduto().getId(), compra.getLocalEstoque().getId(), "COMPRA", SentidoMovimentacao.ENTRADA,
                    item.getQuantidade(), item.getUnidade().getId(), custoUnitario,
                    OrigemMovimentacao.COMPRA, compra.getId(), item.getId(), null, compra.getUsuarioId(), null));

            produtoService.alterarPreco(item.getProduto().getId(), item.getProduto().getPrecoVenda(), custoUnitario,
                    "Custo atualizado pela confirmação da compra #" + compra.getId());
        }

        compra.marcarConfirmada();
        compra = compraRepository.save(compra);

        geradorContaPagar.gerar(compra);

        auditoriaService.registrar(EventoAuditoria.de("COMPRA_CONFIRMADA", "compra", compra.getId(),
                Map.of("status", StatusCompra.RASCUNHO.name()),
                Map.of("status", StatusCompra.CONFIRMADA.name(), "total", compra.getTotal()),
                "Compra confirmada"));

        return compra;
    }

    private Compra buscarRascunho(Long compraId) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new NoSuchElementException("Compra " + compraId + " não encontrada"));
        if (compra.getStatus() != StatusCompra.RASCUNHO) {
            throw new IllegalStateException("Compra " + compraId + " não está em rascunho (status atual: " + compra.getStatus() + ")");
        }
        return compra;
    }
}
