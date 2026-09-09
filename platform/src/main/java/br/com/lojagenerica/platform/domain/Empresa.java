package br.com.lojagenerica.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Uma linha por tenant, sempre no schema "plataforma" (nunca no schema de
 * um tenant específico). {@code schemaNome} é o identificador estável
 * (empresa_007) — {@code nomeFantasia} pode mudar livremente sem afetar
 * schema nem dado nenhum.
 */
@Entity
@Table(name = "empresa")
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schema_nome", nullable = false, unique = true, length = 63)
    private String schemaNome;

    @Column(name = "razao_social", nullable = false, length = 200)
    private String razaoSocial;

    @Column(name = "nome_fantasia", length = 200)
    private String nomeFantasia;

    @Column(length = 32)
    private String documento;

    @Column(nullable = false, unique = true, length = 100)
    private String subdominio;

    @Column(name = "datasource_ref", nullable = false, length = 50)
    private String datasourceRef = "primary";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusEmpresa status = StatusEmpresa.PROVISIONANDO;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "ativada_em")
    private Instant ativadaEm;

    protected Empresa() {
    }

    public Empresa(String schemaNome, String razaoSocial, String nomeFantasia, String documento, String subdominio) {
        this.schemaNome = schemaNome;
        this.razaoSocial = razaoSocial;
        this.nomeFantasia = nomeFantasia;
        this.documento = documento;
        this.subdominio = subdominio;
    }

    public Long getId() {
        return id;
    }

    public String getSchemaNome() {
        return schemaNome;
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public String getNomeFantasia() {
        return nomeFantasia;
    }

    public String getDocumento() {
        return documento;
    }

    public String getSubdominio() {
        return subdominio;
    }

    public String getDatasourceRef() {
        return datasourceRef;
    }

    public StatusEmpresa getStatus() {
        return status;
    }

    public void marcarAtiva() {
        this.status = StatusEmpresa.ATIVA;
        this.ativadaEm = Instant.now();
    }

    public void marcarFalha(StatusEmpresa motivo) {
        this.status = motivo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtivadaEm() {
        return ativadaEm;
    }
}
