package br.com.lojagenerica.core.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "registro_auditoria")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ocorrido_em", nullable = false)
    private Instant ocorridoEm = Instant.now();

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "usuario_email_snapshot")
    private String usuarioEmailSnapshot;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "terminal_id")
    private Long terminalId;

    @Column(nullable = false, length = 100)
    private String evento;

    @Column(nullable = false, length = 100)
    private String entidade;

    @Column(name = "entidade_id")
    private Long entidadeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valores_antes", columnDefinition = "jsonb")
    private String valoresAntes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valores_depois", columnDefinition = "jsonb")
    private String valoresDepois;

    @Column(length = 500)
    private String motivo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String contexto;

    protected RegistroAuditoria() {
    }

    public RegistroAuditoria(Long usuarioId, String usuarioEmailSnapshot, String evento, String entidade,
                              Long entidadeId, String valoresAntes, String valoresDepois, String motivo) {
        this.usuarioId = usuarioId;
        this.usuarioEmailSnapshot = usuarioEmailSnapshot;
        this.evento = evento;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.valoresAntes = valoresAntes;
        this.valoresDepois = valoresDepois;
        this.motivo = motivo;
    }

    public Long getId() {
        return id;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getEvento() {
        return evento;
    }

    public String getEntidade() {
        return entidade;
    }

    public Long getEntidadeId() {
        return entidadeId;
    }

    public String getValoresAntes() {
        return valoresAntes;
    }

    public String getValoresDepois() {
        return valoresDepois;
    }

    public String getMotivo() {
        return motivo;
    }
}
