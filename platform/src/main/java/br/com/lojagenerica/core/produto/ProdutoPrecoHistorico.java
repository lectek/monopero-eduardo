package br.com.lojagenerica.core.produto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * "Alteração de preço" é item #1 da lista de auditoria obrigatória do spec —
 * tabela dedicada (além do {@code registro_auditoria} genérico) porque é a
 * base direta do módulo de Rentabilidade (Fase H): margem tem que ser
 * calculada contra o custo vigente na hora da venda, não o de hoje.
 */
@Entity
@Table(name = "produto_preco_historico")
public class ProdutoPrecoHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @Column(name = "preco_anterior", precision = 15, scale = 4)
    private BigDecimal precoAnterior;

    @Column(name = "preco_novo", precision = 15, scale = 4)
    private BigDecimal precoNovo;

    @Column(name = "custo_anterior", precision = 15, scale = 4)
    private BigDecimal custoAnterior;

    @Column(name = "custo_novo", precision = 15, scale = 4)
    private BigDecimal custoNovo;

    @Column(name = "vigente_de", nullable = false)
    private Instant vigenteDe = Instant.now();

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(length = 500)
    private String motivo;

    protected ProdutoPrecoHistorico() {
    }

    public ProdutoPrecoHistorico(Produto produto, BigDecimal precoAnterior, BigDecimal precoNovo,
                                  BigDecimal custoAnterior, BigDecimal custoNovo, Long usuarioId, String motivo) {
        this.produto = produto;
        this.precoAnterior = precoAnterior;
        this.precoNovo = precoNovo;
        this.custoAnterior = custoAnterior;
        this.custoNovo = custoNovo;
        this.usuarioId = usuarioId;
        this.motivo = motivo;
    }

    public Long getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public BigDecimal getPrecoAnterior() {
        return precoAnterior;
    }

    public BigDecimal getPrecoNovo() {
        return precoNovo;
    }

    public BigDecimal getCustoAnterior() {
        return custoAnterior;
    }

    public BigDecimal getCustoNovo() {
        return custoNovo;
    }

    public Instant getVigenteDe() {
        return vigenteDe;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getMotivo() {
        return motivo;
    }
}
