package br.com.lojagenerica.core.compra;

import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.produto.Produto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rateio de frete/outros custos por item — é o que alimenta custo
 * considerado depois (módulo de Rentabilidade, Fase H). O rateio em si
 * (como dividir o frete total da compra entre os itens) é decisão de
 * {@link CompraService}, esta classe só guarda o resultado.
 */
@Entity
@Table(name = "item_compra")
public class ItemCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id")
    private Compra compra;

    @ManyToOne(optional = false)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal quantidade;

    @ManyToOne(optional = false)
    @JoinColumn(name = "unidade_id")
    private UnidadeMedida unidade;

    @Column(name = "fator_conversao", nullable = false, precision = 18, scale = 6)
    private BigDecimal fatorConversao = BigDecimal.ONE;

    @Column(name = "quantidade_base", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantidadeBase;

    @Column(name = "preco_unitario", nullable = false, precision = 15, scale = 4)
    private BigDecimal precoUnitario;

    @Column(name = "desconto_valor", nullable = false, precision = 15, scale = 4)
    private BigDecimal descontoValor = BigDecimal.ZERO;

    @Column(name = "rateio_frete", nullable = false, precision = 15, scale = 4)
    private BigDecimal rateioFrete = BigDecimal.ZERO;

    @Column(name = "rateio_outros_custos", nullable = false, precision = 15, scale = 4)
    private BigDecimal rateioOutrosCustos = BigDecimal.ZERO;

    /** Coluna gerada pelo banco (V007) — só leitura pro JPA, nunca escrita daqui. */
    @Column(name = "custo_unitario_final", insertable = false, updatable = false, precision = 15, scale = 4)
    private BigDecimal custoUnitarioFinalPersistido;

    @Column(name = "total_linha", nullable = false, precision = 15, scale = 4)
    private BigDecimal totalLinha;

    protected ItemCompra() {
    }

    public ItemCompra(Produto produto, BigDecimal quantidade, UnidadeMedida unidade, BigDecimal fatorConversao,
                       BigDecimal quantidadeBase, BigDecimal precoUnitario, BigDecimal descontoValor) {
        this.produto = produto;
        this.quantidade = quantidade;
        this.unidade = unidade;
        this.fatorConversao = fatorConversao;
        this.quantidadeBase = quantidadeBase;
        this.precoUnitario = precoUnitario;
        this.descontoValor = descontoValor;
        this.totalLinha = precoUnitario.multiply(quantidade).subtract(descontoValor);
    }

    void pertencerA(Compra compra) {
        this.compra = compra;
    }

    void aplicarRateio(BigDecimal rateioFrete, BigDecimal rateioOutrosCustos) {
        this.rateioFrete = rateioFrete;
        this.rateioOutrosCustos = rateioOutrosCustos;
    }

    /**
     * Mesma fórmula da coluna gerada no banco (V007) — calculada em Java
     * porque o valor persistido só fica visível após um refresh da
     * entidade, e o ledger de estoque (MovimentacaoEstoqueService) precisa
     * do custo unitário na hora de confirmar, não depois de reler do banco.
     */
    public BigDecimal custoUnitarioFinal() {
        if (quantidadeBase.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalComRateio = precoUnitario.multiply(quantidade)
                .subtract(descontoValor).add(rateioFrete).add(rateioOutrosCustos);
        return totalComRateio.divide(quantidadeBase, 4, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public UnidadeMedida getUnidade() {
        return unidade;
    }

    public BigDecimal getQuantidadeBase() {
        return quantidadeBase;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    public BigDecimal getTotalLinha() {
        return totalLinha;
    }
}
