package br.com.lojagenerica.core.venda;

import br.com.lojagenerica.core.cadastro.FormaPagamento;
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
import java.time.Instant;

/** 1:N de propósito — pagamento dividido (parte dinheiro, parte cartão) é table stakes. */
@Entity
@Table(name = "venda_pagamento")
public class VendaPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id")
    private Venda venda;

    @ManyToOne(optional = false)
    @JoinColumn(name = "forma_pagamento_id")
    private FormaPagamento formaPagamento;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal valor;

    private Short parcelas;

    @Column(name = "valor_recebido", precision = 15, scale = 4)
    private BigDecimal valorRecebido;

    @Column(precision = 15, scale = 4)
    private BigDecimal troco;

    @Column(name = "referencia_externa", length = 100)
    private String referenciaExterna;

    @Column(name = "recebido_em", nullable = false)
    private Instant recebidoEm = Instant.now();

    protected VendaPagamento() {
    }

    public VendaPagamento(FormaPagamento formaPagamento, BigDecimal valor, BigDecimal valorRecebido, BigDecimal troco) {
        this.formaPagamento = formaPagamento;
        this.valor = valor;
        this.valorRecebido = valorRecebido;
        this.troco = troco;
    }

    void pertencerA(Venda venda) {
        this.venda = venda;
    }

    public Long getId() {
        return id;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public BigDecimal getValorRecebido() {
        return valorRecebido;
    }

    public BigDecimal getTroco() {
        return troco;
    }
}
