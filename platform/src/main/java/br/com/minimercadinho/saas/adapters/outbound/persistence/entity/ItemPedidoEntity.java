package br.com.minimercadinho.saas.adapters.outbound.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Linha de pedido — simplificado em relação ao ItemPedidoEntity do
 * ParaisoPet: lá é @ManyToOne para um ProdutoEntity com ID numérico próprio
 * (schema MySQL). Aqui o produto real é a tabela {@code products} do rbp.db,
 * com chave natural (nome, cor, peso) e sem ID — então guardamos essa chave
 * como snapshot direto, sem relação JPA.
 */
@Entity
@Table(name = "item_pedido")
public class ItemPedidoEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private PedidoEntity pedido;

    @Column(name = "produto_nome", nullable = false)
    private String produtoNome;

    @Column(name = "produto_cor")
    private String produtoCor;

    @Column(name = "produto_peso")
    private String produtoPeso;

    @Column(name = "produto_codigo_barras")
    private String produtoCodigoBarras;

    @NotNull
    @Min(value = 1, message = "Quantidade deve ser no mínimo 1")
    @Column(nullable = false)
    private Integer quantidade;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true, message = "Preço unitário não pode ser negativo")
    @Column(name = "preco_unitario", nullable = false)
    private BigDecimal precoUnitario;

    public Long getId() {
        return id;
    }

    public PedidoEntity getPedido() {
        return pedido;
    }

    public void setPedido(PedidoEntity pedido) {
        this.pedido = pedido;
    }

    public String getProdutoNome() {
        return produtoNome;
    }

    public void setProdutoNome(String produtoNome) {
        this.produtoNome = produtoNome;
    }

    public String getProdutoCor() {
        return produtoCor;
    }

    public void setProdutoCor(String produtoCor) {
        this.produtoCor = produtoCor;
    }

    public String getProdutoPeso() {
        return produtoPeso;
    }

    public void setProdutoPeso(String produtoPeso) {
        this.produtoPeso = produtoPeso;
    }

    public String getProdutoCodigoBarras() {
        return produtoCodigoBarras;
    }

    public void setProdutoCodigoBarras(String produtoCodigoBarras) {
        this.produtoCodigoBarras = produtoCodigoBarras;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    public void setPrecoUnitario(BigDecimal precoUnitario) {
        this.precoUnitario = precoUnitario;
    }

    public BigDecimal getSubtotal() {
        if (precoUnitario == null || quantidade == null) {
            return BigDecimal.ZERO;
        }
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemPedidoEntity that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
