package br.com.lojagenerica.core.produto;

import br.com.lojagenerica.core.cadastro.Categoria;
import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.cadastro.Marca;
import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Substitui o catálogo do IMS (chave natural nome+cor+peso, sem ID
 * numérico — ver {@code adapters.outbound.ims.ImsProdutoRepository}, ainda
 * usado pelo checkout/storefront antigos até a Fase C repontá-los pra cá).
 * Só {@code nome} e {@code unidadeEstoque} são obrigatórios — o mínimo pra
 * um produto existir e ser contado. {@code controlaEstoque=false} cobre
 * serviços/mão-de-obra, que um comércio genérico encontra de cara.
 */
@Entity
@Table(name = "produto")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false)
    private String nome;

    @Column(name = "codigo_interno", unique = true)
    private String codigoInterno;

    @Column(columnDefinition = "text")
    private String descricao;

    @ManyToOne
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne
    @JoinColumn(name = "marca_id")
    private Marca marca;

    @Column(length = 150)
    private String fabricante;

    @ManyToOne(optional = false)
    @JoinColumn(name = "unidade_estoque_id")
    private UnidadeMedida unidadeEstoque;

    @ManyToOne
    @JoinColumn(name = "unidade_venda_id")
    private UnidadeMedida unidadeVenda;

    @Column(name = "preco_venda", precision = 15, scale = 4)
    private BigDecimal precoVenda;

    @Column(name = "custo_aquisicao", precision = 15, scale = 4)
    private BigDecimal custoAquisicao;

    @Column(name = "estoque_minimo", precision = 18, scale = 6)
    private BigDecimal estoqueMinimo;

    @Column(name = "estoque_maximo", precision = 18, scale = 6)
    private BigDecimal estoqueMaximo;

    @ManyToOne
    @JoinColumn(name = "local_estoque_padrao_id")
    private LocalEstoque localEstoquePadrao;

    @Column(precision = 15, scale = 4)
    private BigDecimal peso;

    @Column(precision = 15, scale = 4)
    private BigDecimal altura;

    @Column(precision = 15, scale = 4)
    private BigDecimal largura;

    @Column(precision = 15, scale = 4)
    private BigDecimal profundidade;

    @Column(name = "controla_estoque", nullable = false)
    private boolean controlaEstoque = true;

    @Column(name = "permite_venda_sem_estoque", nullable = false)
    private boolean permiteVendaSemEstoque = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusProduto status = StatusProduto.ATIVO;

    @Column(columnDefinition = "text")
    private String observacoes;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm = Instant.now();

    @Version
    private long versao;

    protected Produto() {
    }

    public Produto(String nome, UnidadeMedida unidadeEstoque) {
        this.nome = nome;
        this.unidadeEstoque = unidadeEstoque;
    }

    public Long getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCodigoInterno() {
        return codigoInterno;
    }

    public void setCodigoInterno(String codigoInterno) {
        this.codigoInterno = codigoInterno;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public void setCategoria(Categoria categoria) {
        this.categoria = categoria;
    }

    public Marca getMarca() {
        return marca;
    }

    public void setMarca(Marca marca) {
        this.marca = marca;
    }

    public String getFabricante() {
        return fabricante;
    }

    public void setFabricante(String fabricante) {
        this.fabricante = fabricante;
    }

    public UnidadeMedida getUnidadeEstoque() {
        return unidadeEstoque;
    }

    public void setUnidadeEstoque(UnidadeMedida unidadeEstoque) {
        this.unidadeEstoque = unidadeEstoque;
    }

    public UnidadeMedida getUnidadeVenda() {
        return unidadeVenda;
    }

    public void setUnidadeVenda(UnidadeMedida unidadeVenda) {
        this.unidadeVenda = unidadeVenda;
    }

    public BigDecimal getPrecoVenda() {
        return precoVenda;
    }

    public BigDecimal getCustoAquisicao() {
        return custoAquisicao;
    }

    /** Alteração de preço/custo é auditada — ver ProdutoService.alterarPreco(). */
    void definirPrecoECusto(BigDecimal precoVenda, BigDecimal custoAquisicao) {
        this.precoVenda = precoVenda;
        this.custoAquisicao = custoAquisicao;
        this.atualizadoEm = Instant.now();
    }

    public BigDecimal getEstoqueMinimo() {
        return estoqueMinimo;
    }

    public void setEstoqueMinimo(BigDecimal estoqueMinimo) {
        this.estoqueMinimo = estoqueMinimo;
    }

    public BigDecimal getEstoqueMaximo() {
        return estoqueMaximo;
    }

    public void setEstoqueMaximo(BigDecimal estoqueMaximo) {
        this.estoqueMaximo = estoqueMaximo;
    }

    public LocalEstoque getLocalEstoquePadrao() {
        return localEstoquePadrao;
    }

    public void setLocalEstoquePadrao(LocalEstoque localEstoquePadrao) {
        this.localEstoquePadrao = localEstoquePadrao;
    }

    public BigDecimal getPeso() {
        return peso;
    }

    public void setPeso(BigDecimal peso) {
        this.peso = peso;
    }

    public boolean isControlaEstoque() {
        return controlaEstoque;
    }

    public void setControlaEstoque(boolean controlaEstoque) {
        this.controlaEstoque = controlaEstoque;
    }

    public boolean isPermiteVendaSemEstoque() {
        return permiteVendaSemEstoque;
    }

    public void setPermiteVendaSemEstoque(boolean permiteVendaSemEstoque) {
        this.permiteVendaSemEstoque = permiteVendaSemEstoque;
    }

    public StatusProduto getStatus() {
        return status;
    }

    public void setStatus(StatusProduto status) {
        this.status = status;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public enum StatusProduto {
        ATIVO, INATIVO, DESCONTINUADO
    }
}
