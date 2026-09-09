package br.com.lojagenerica.core.cadastro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "forma_pagamento")
public class FormaPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NaturezaFormaPagamento natureza;

    @Column(name = "afeta_caixa", nullable = false)
    private boolean afetaCaixa = true;

    @Column(name = "permite_parcelamento", nullable = false)
    private boolean permiteParcelamento = false;

    @Column(name = "max_parcelas")
    private Short maxParcelas;

    @Column(name = "prazo_recebimento_dias")
    private Integer prazoRecebimentoDias;

    @Column(name = "taxa_percentual", precision = 6, scale = 3)
    private BigDecimal taxaPercentual;

    @Column(nullable = false)
    private boolean ativo = true;

    protected FormaPagamento() {
    }

    public FormaPagamento(String nome, NaturezaFormaPagamento natureza, boolean afetaCaixa) {
        this.nome = nome;
        this.natureza = natureza;
        this.afetaCaixa = afetaCaixa;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public NaturezaFormaPagamento getNatureza() {
        return natureza;
    }

    public boolean isAfetaCaixa() {
        return afetaCaixa;
    }

    public boolean isPermiteParcelamento() {
        return permiteParcelamento;
    }

    public void setPermiteParcelamento(boolean permiteParcelamento) {
        this.permiteParcelamento = permiteParcelamento;
    }

    public Short getMaxParcelas() {
        return maxParcelas;
    }

    public void setMaxParcelas(Short maxParcelas) {
        this.maxParcelas = maxParcelas;
    }

    public Integer getPrazoRecebimentoDias() {
        return prazoRecebimentoDias;
    }

    public void setPrazoRecebimentoDias(Integer prazoRecebimentoDias) {
        this.prazoRecebimentoDias = prazoRecebimentoDias;
    }

    public BigDecimal getTaxaPercentual() {
        return taxaPercentual;
    }

    public void setTaxaPercentual(BigDecimal taxaPercentual) {
        this.taxaPercentual = taxaPercentual;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
