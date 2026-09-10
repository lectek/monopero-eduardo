package br.com.lojagenerica.core.cadastro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Gera as parcelas de conta a pagar na confirmação da compra (ver GeradorContaPagar, Fase E). */
@Entity
@Table(name = "condicao_pagamento")
public class CondicaoPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false)
    private short parcelas = 1;

    @Column(name = "intervalo_dias", nullable = false)
    private int intervaloDias = 0;

    @Column(name = "entrada_percentual", precision = 6, scale = 3)
    private BigDecimal entradaPercentual;

    @Column(nullable = false)
    private boolean ativo = true;

    protected CondicaoPagamento() {
    }

    public CondicaoPagamento(String nome, short parcelas, int intervaloDias) {
        this.nome = nome;
        this.parcelas = parcelas;
        this.intervaloDias = intervaloDias;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public short getParcelas() {
        return parcelas;
    }

    public int getIntervaloDias() {
        return intervaloDias;
    }

    public BigDecimal getEntradaPercentual() {
        return entradaPercentual;
    }

    public void setEntradaPercentual(BigDecimal entradaPercentual) {
        this.entradaPercentual = entradaPercentual;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
