package br.com.lojagenerica.core.compra;

import br.com.lojagenerica.core.cadastro.CondicaoPagamento;
import br.com.lojagenerica.core.cadastro.FormaPagamento;
import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.parceiro.Fornecedor;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link #confirmar} é o primeiro template de transação cross-módulo do
 * sistema: numa chamada só, grava ledger de entrada + atualiza custo médio
 * + (quando Financeiro existir, Fase E) gera conta a pagar + evento de
 * auditoria. Ver {@link br.com.lojagenerica.core.compra.CompraService}.
 */
@Entity
@Table(name = "compra")
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String numero;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao = LocalDate.now();

    @Column(name = "data_entrada")
    private LocalDate dataEntrada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusCompra status = StatusCompra.RASCUNHO;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "desconto_valor", nullable = false, precision = 15, scale = 4)
    private BigDecimal descontoValor = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal frete = BigDecimal.ZERO;

    @Column(name = "outros_custos", nullable = false, precision = 15, scale = 4)
    private BigDecimal outrosCustos = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    @ManyToOne
    @JoinColumn(name = "condicao_pagamento_id")
    private CondicaoPagamento condicaoPagamento;

    @ManyToOne
    @JoinColumn(name = "forma_pagamento_id")
    private FormaPagamento formaPagamento;

    @ManyToOne(optional = false)
    @JoinColumn(name = "local_estoque_id")
    private LocalEstoque localEstoque;

    @Column(columnDefinition = "text")
    private String observacoes;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "confirmada_em")
    private Instant confirmadaEm;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id")
    private List<ItemCompra> itens = new ArrayList<>();

    protected Compra() {
    }

    public Compra(Fornecedor fornecedor, LocalEstoque localEstoque, Long usuarioId) {
        this.fornecedor = fornecedor;
        this.localEstoque = localEstoque;
        this.usuarioId = usuarioId;
    }

    public void adicionarItem(ItemCompra item) {
        item.pertencerA(this);
        this.itens.add(item);
    }

    public void definirCondicoes(CondicaoPagamento condicaoPagamento, FormaPagamento formaPagamento,
                                  BigDecimal frete, BigDecimal outrosCustos, BigDecimal descontoValor) {
        this.condicaoPagamento = condicaoPagamento;
        this.formaPagamento = formaPagamento;
        this.frete = frete != null ? frete : BigDecimal.ZERO;
        this.outrosCustos = outrosCustos != null ? outrosCustos : BigDecimal.ZERO;
        this.descontoValor = descontoValor != null ? descontoValor : BigDecimal.ZERO;
    }

    /**
     * Rateia frete/outros custos proporcionalmente ao subtotal de cada item
     * e recalcula os totais da compra. Chamado por {@code CompraService}
     * antes de confirmar — não faz sentido existir "compra com total
     * inconsistente com a soma dos itens".
     */
    void recalcularTotais() {
        this.subtotal = itens.stream().map(ItemCompra::getTotalLinha).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (subtotal.signum() > 0) {
            for (ItemCompra item : itens) {
                BigDecimal proporcao = item.getTotalLinha().divide(subtotal, 10, java.math.RoundingMode.HALF_UP);
                item.aplicarRateio(
                        frete.multiply(proporcao).setScale(4, java.math.RoundingMode.HALF_UP),
                        outrosCustos.multiply(proporcao).setScale(4, java.math.RoundingMode.HALF_UP));
            }
        }
        this.total = subtotal.subtract(descontoValor).add(frete).add(outrosCustos);
    }

    public void marcarConfirmada() {
        this.status = StatusCompra.CONFIRMADA;
        this.confirmadaEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Fornecedor getFornecedor() {
        return fornecedor;
    }

    public StatusCompra getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getFrete() {
        return frete;
    }

    public BigDecimal getOutrosCustos() {
        return outrosCustos;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public CondicaoPagamento getCondicaoPagamento() {
        return condicaoPagamento;
    }

    public LocalEstoque getLocalEstoque() {
        return localEstoque;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public List<ItemCompra> getItens() {
        return itens;
    }

    public Instant getConfirmadaEm() {
        return confirmadaEm;
    }
}
