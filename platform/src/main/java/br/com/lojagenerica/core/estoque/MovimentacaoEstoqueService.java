package br.com.lojagenerica.core.estoque;

import br.com.lojagenerica.core.cadastro.ConversaoUnidade;
import br.com.lojagenerica.core.cadastro.ConversaoUnidadeRepository;
import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.cadastro.LocalEstoqueRepository;
import br.com.lojagenerica.core.cadastro.SentidoMovimentacao;
import br.com.lojagenerica.core.cadastro.TipoMovimentacao;
import br.com.lojagenerica.core.cadastro.TipoMovimentacaoRepository;
import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
import br.com.lojagenerica.core.produto.Produto;
import br.com.lojagenerica.core.produto.ProdutoRepository;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chokepoint único de escrita no ledger de estoque — todo módulo que
 * movimenta estoque (Compras, Vendas, Devoluções, Inventário, hoje só
 * ajuste manual) passa por aqui, nunca grava {@code movimentacao_estoque}
 * direto.
 */
@Service
public class MovimentacaoEstoqueService {

    private static final Logger log = LoggerFactory.getLogger(MovimentacaoEstoqueService.class);

    private final MovimentacaoEstoqueRepository movimentacaoRepository;
    private final SaldoEstoqueRepository saldoRepository;
    private final ProdutoRepository produtoRepository;
    private final LocalEstoqueRepository localEstoqueRepository;
    private final TipoMovimentacaoRepository tipoMovimentacaoRepository;
    private final UnidadeMedidaRepository unidadeMedidaRepository;
    private final ConversaoUnidadeRepository conversaoUnidadeRepository;

    public MovimentacaoEstoqueService(
            MovimentacaoEstoqueRepository movimentacaoRepository, SaldoEstoqueRepository saldoRepository,
            ProdutoRepository produtoRepository, LocalEstoqueRepository localEstoqueRepository,
            TipoMovimentacaoRepository tipoMovimentacaoRepository, UnidadeMedidaRepository unidadeMedidaRepository,
            ConversaoUnidadeRepository conversaoUnidadeRepository) {
        this.movimentacaoRepository = movimentacaoRepository;
        this.saldoRepository = saldoRepository;
        this.produtoRepository = produtoRepository;
        this.localEstoqueRepository = localEstoqueRepository;
        this.tipoMovimentacaoRepository = tipoMovimentacaoRepository;
        this.unidadeMedidaRepository = unidadeMedidaRepository;
        this.conversaoUnidadeRepository = conversaoUnidadeRepository;
    }

    /**
     * Idempotente quando {@code origemId} é informado: uma segunda chamada
     * com o mesmo (origemTipo, origemId, origemItemId) devolve a
     * movimentação já existente em vez de duplicar (mesma garantia que o
     * índice único do banco dá — aqui só evitamos a exceção virar erro pro
     * chamador, ver docs/CONTEXTO.md).
     */
    @Transactional
    public MovimentacaoEstoque registrar(RegistrarMovimentacaoCommand cmd) {
        if (cmd.origemId() != null) {
            var existente = movimentacaoRepository.findByOrigemTipoAndOrigemIdAndOrigemItemId(
                    cmd.origemTipo(), cmd.origemId(), cmd.origemItemId());
            if (existente.isPresent()) {
                log.info("Movimentação já registrada para origem {}/{}/{} — idempotente, ignorando.",
                        cmd.origemTipo(), cmd.origemId(), cmd.origemItemId());
                return existente.get();
            }
        }

        Produto produto = produtoRepository.findById(cmd.produtoId())
                .orElseThrow(() -> new NoSuchElementException("Produto " + cmd.produtoId() + " não encontrado"));
        LocalEstoque local = localEstoqueRepository.findById(cmd.localEstoqueId())
                .orElseThrow(() -> new NoSuchElementException("Local de estoque " + cmd.localEstoqueId() + " não encontrado"));
        TipoMovimentacao tipo = tipoMovimentacaoRepository.findByCodigo(cmd.tipoMovimentacaoCodigo())
                .orElseThrow(() -> new NoSuchElementException("Tipo de movimentação '" + cmd.tipoMovimentacaoCodigo() + "' não encontrado"));
        UnidadeMedida unidade = unidadeMedidaRepository.findById(cmd.unidadeId())
                .orElseThrow(() -> new NoSuchElementException("Unidade " + cmd.unidadeId() + " não encontrada"));

        if (tipo.isExigeMotivo() && (cmd.motivo() == null || cmd.motivo().isBlank())) {
            throw new IllegalArgumentException("Tipo de movimentação '" + tipo.getCodigo() + "' exige motivo");
        }

        BigDecimal fator = resolverFatorConversao(produto, unidade);
        BigDecimal quantidadeBaseAbsoluta = cmd.quantidade().multiply(fator);
        BigDecimal quantidadeBaseAssinada = cmd.sentido() == SentidoMovimentacao.SAIDA
                ? quantidadeBaseAbsoluta.negate()
                : quantidadeBaseAbsoluta;

        SaldoEstoque saldo = saldoRepository.buscarParaAtualizar(produto.getId(), local.getId())
                .orElseGet(() -> new SaldoEstoque(produto, local));
        saldo.aplicar(quantidadeBaseAssinada, cmd.custoUnitario());
        BigDecimal saldoApos = saldo.getQuantidade();

        MovimentacaoEstoque movimentacao = new MovimentacaoEstoque(
                produto, local, tipo, cmd.sentido(), cmd.quantidade(), unidade, fator, quantidadeBaseAssinada,
                cmd.custoUnitario(), saldoApos, cmd.origemTipo(), cmd.origemId(), cmd.origemItemId(),
                cmd.terminalId(), cmd.usuarioId(), cmd.motivo());

        try {
            movimentacao = movimentacaoRepository.save(movimentacao);
        } catch (DataIntegrityViolationException e) {
            // Corrida rara: outra transação registrou a mesma origem entre o
            // check de idempotência acima e este insert. Trata do mesmo jeito.
            return movimentacaoRepository.findByOrigemTipoAndOrigemIdAndOrigemItemId(
                            cmd.origemTipo(), cmd.origemId(), cmd.origemItemId())
                    .orElseThrow(() -> e);
        }
        saldoRepository.save(saldo);
        return movimentacao;
    }

    /**
     * Público também pra quem precisa calcular quantidade_base ANTES de
     * chamar {@link #registrar} — ex. CompraService monta o item_compra
     * (que guarda seu próprio fator/quantidade_base) usando esta mesma
     * resolução, pra não ter duas lógicas de conversão divergentes.
     */
    public BigDecimal resolverFatorConversao(Produto produto, UnidadeMedida unidade) {
        if (unidade.getId().equals(produto.getUnidadeEstoque().getId())) {
            return BigDecimal.ONE;
        }
        return conversaoUnidadeRepository
                .findByUnidadeOrigemIdAndUnidadeDestinoIdAndProdutoId(
                        unidade.getId(), produto.getUnidadeEstoque().getId(), produto.getId())
                .or(() -> conversaoUnidadeRepository.findByUnidadeOrigemIdAndUnidadeDestinoIdAndProdutoIdIsNull(
                        unidade.getId(), produto.getUnidadeEstoque().getId()))
                .map(ConversaoUnidade::getFator)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Sem conversão cadastrada de " + unidade.getCodigo() + " para "
                                + produto.getUnidadeEstoque().getCodigo() + " (produto " + produto.getId() + ")"));
    }
}
