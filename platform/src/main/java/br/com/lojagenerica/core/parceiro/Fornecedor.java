package br.com.lojagenerica.core.parceiro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Tabela separada de Cliente (que só chega na Fase C) — regras de
 * unicidade e ciclo de vida diferentes o suficiente pra não valer juntar
 * numa "pessoa" genérica (ver docs/CONTEXTO.md).
 */
@Entity
@Table(name = "fornecedor")
public class Fornecedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "razao_social", nullable = false, length = 200)
    private String razaoSocial;

    @Column(name = "nome_fantasia", length = 200)
    private String nomeFantasia;

    @Column(length = 32)
    private String documento;

    @Column(name = "inscricao_estadual", length = 32)
    private String inscricaoEstadual;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String endereco;

    @Column(columnDefinition = "text")
    private String observacoes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusFornecedor status = StatusFornecedor.ATIVO;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    protected Fornecedor() {
    }

    public Fornecedor(String razaoSocial) {
        this.razaoSocial = razaoSocial;
    }

    public Long getId() {
        return id;
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public void setRazaoSocial(String razaoSocial) {
        this.razaoSocial = razaoSocial;
    }

    public String getNomeFantasia() {
        return nomeFantasia;
    }

    public void setNomeFantasia(String nomeFantasia) {
        this.nomeFantasia = nomeFantasia;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public String getInscricaoEstadual() {
        return inscricaoEstadual;
    }

    public void setInscricaoEstadual(String inscricaoEstadual) {
        this.inscricaoEstadual = inscricaoEstadual;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public StatusFornecedor getStatus() {
        return status;
    }

    public void setStatus(StatusFornecedor status) {
        this.status = status;
    }

    public enum StatusFornecedor {
        ATIVO, INATIVO
    }
}
