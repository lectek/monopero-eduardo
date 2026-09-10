package br.com.lojagenerica.core.venda;

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

/**
 * {@code custoUnitarioSnapshot} é a base inteira do módulo de
 * Rentabilidade (Fase H) — margem usa o custo vigente NA VENDA, não o de
 * hoje. {@code descricaoSnapshot}/{@code codigoSnapshot} preservam o que o
 * cliente comprou mesmo que o produto seja renomeado depois.
 */
@Entity
@Table(name = "item_venda")
public class ItemVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id")
    private Venda venda;

    @ManyToOne(optional = false)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @Column(name = "descricao_snapshot", nullable = false, length = 255)
    private String descricaoSnapshot;

    @Column(name = "codigo_snapshot", length = 100)
    private String codigoSnapshot;

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

    @Column(name = "total_linha", nullable = false, precision = 15, scale = 4)
    private BigDecimal totalLinha;

    @Column(name = "custo_unitario_snapshot", precision = 15, scale = 4)
    private BigDecimal custoUnitarioSnapshot;

    @Column(name = "quantidade_devolvida", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantidadeDevolvida = BigDecimal.ZERO;

    protected ItemVenda() {
    }

    public ItemVenda(Produto produto, BigDecimal quantidade, UnidadeMedida unidade, BigDecimal fatorConversao,
                      BigDecimal quantidadeBase, BigDecimal precoUnitario, BigDecimal descontoValor,
                      BigDecimal custoUnitarioSnapshot) {
        this.produto = produto;
        this.descricaoSnapshot = produto.getNome();
        this.codigoSnapshot = produto.getCodigoInterno();
        this.quantidade = quantidade;
        this.unidade = unidade;
        this.fatorConversao = fatorConversao;
        this.quantidadeBase = quantidadeBase;
        this.precoUnitario = precoUnitario;
        this.descontoValor = descontoValor != null ? descontoValor : BigDecimal.ZERO;
        this.totalLinha = precoUnitario.multiply(quantidade).subtract(this.descontoValor);
        this.custoUnitarioSnapshot = custoUnitarioSnapshot;
    }

    void pertencerA(Venda venda) {
        this.venda = venda;
    }

    public Long getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public String getDescricaoSnapshot() {
        return descricaoSnapshot;
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

    public BigDecimal getDescontoValor() {
        return descontoValor;
    }

    public BigDecimal getTotalLinha() {
        return totalLinha;
    }

    public BigDecimal getCustoUnitarioSnapshot() {
        return custoUnitarioSnapshot;
    }
}
