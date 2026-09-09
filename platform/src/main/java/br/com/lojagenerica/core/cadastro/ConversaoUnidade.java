package br.com.lojagenerica.core.cadastro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * {@code produtoId} nulo = regra global (ex.: 1m = 100cm); preenchido = regra
 * específica daquele produto (ex.: a caixa DESTE produto tem 12 unidades,
 * outro produto pode ter caixa de 24) — resolução: específica primeiro,
 * senão global. Sem FK JPA pra Produto de propósito (evita acoplamento
 * cadastro→produto; a FK real já existe no schema via migration).
 */
@Entity
@Table(name = "conversao_unidade")
public class ConversaoUnidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "unidade_origem_id")
    private UnidadeMedida unidadeOrigem;

    @ManyToOne(optional = false)
    @JoinColumn(name = "unidade_destino_id")
    private UnidadeMedida unidadeDestino;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal fator;

    @Column(name = "produto_id")
    private Long produtoId;

    protected ConversaoUnidade() {
    }

    public ConversaoUnidade(UnidadeMedida unidadeOrigem, UnidadeMedida unidadeDestino, BigDecimal fator, Long produtoId) {
        this.unidadeOrigem = unidadeOrigem;
        this.unidadeDestino = unidadeDestino;
        this.fator = fator;
        this.produtoId = produtoId;
    }

    public Long getId() {
        return id;
    }

    public UnidadeMedida getUnidadeOrigem() {
        return unidadeOrigem;
    }

    public UnidadeMedida getUnidadeDestino() {
        return unidadeDestino;
    }

    public BigDecimal getFator() {
        return fator;
    }

    public Long getProdutoId() {
        return produtoId;
    }
}
