package br.com.lojagenerica.pdv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Idempotência de sincronização — o mesmo {@code eventoUuid} nunca é
 * processado duas vezes, mesmo que o terminal reenvie por não ter visto a
 * resposta (conexão caiu depois do commit no servidor).
 */
@Entity
@Table(name = "pdv_evento_recebido")
public class PdvEventoRecebido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_uuid", nullable = false, unique = true)
    private UUID eventoUuid;

    @Column(name = "terminal_id", nullable = false)
    private Long terminalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoEventoPdv tipo;

    @Column(name = "venda_id")
    private Long vendaId;

    @Column(name = "recebido_em", nullable = false)
    private Instant recebidoEm = Instant.now();

    protected PdvEventoRecebido() {
    }

    public PdvEventoRecebido(UUID eventoUuid, Long terminalId, TipoEventoPdv tipo, Long vendaId) {
        this.eventoUuid = eventoUuid;
        this.terminalId = terminalId;
        this.tipo = tipo;
        this.vendaId = vendaId;
    }

    public UUID getEventoUuid() {
        return eventoUuid;
    }

    public TipoEventoPdv getTipo() {
        return tipo;
    }

    public Long getVendaId() {
        return vendaId;
    }
}
