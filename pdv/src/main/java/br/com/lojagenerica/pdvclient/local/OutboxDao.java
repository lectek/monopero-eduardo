package br.com.lojagenerica.pdvclient.local;

import br.com.lojagenerica.pdvclient.shared.TipoEventoPdv;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fila de eventos a enviar ao servidor. {@code venda_local_id} de uma venda
 * e sua linha de outbox são gravados na MESMA transação (ver
 * {@link VendaLocalDao}) — uma venda nunca existe sem seu evento
 * correspondente, então nunca há o que "esquecer" de sincronizar.
 */
public final class OutboxDao {

    private final LocalDb db;

    public OutboxDao(LocalDb db) {
        this.db = db;
    }

    /** Uso isolado (ex.: cancelamento) — abre e fecha sua própria seção sincronizada. */
    public void enfileirar(UUID eventoUuid, TipoEventoPdv tipo, Instant ocorridoEm, String payloadJson) throws SQLException {
        synchronized (db.lock()) {
            inserirLinha(db.connection(), eventoUuid, tipo, ocorridoEm, payloadJson);
        }
    }

    /** Pra ser chamado de dentro de uma transação já aberta por outro DAO (ver {@code VendaLocalDao}). */
    void inserirLinha(Connection conn, UUID eventoUuid, TipoEventoPdv tipo, Instant ocorridoEm, String payloadJson) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sync_outbox (evento_uuid, tipo, ocorrido_em, payload_json, status, tentativas,
                                          proxima_tentativa_em, criado_em)
                VALUES (?, ?, ?, ?, 'PENDENTE', 0, ?, ?);
                """)) {
            String agora = Instant.now().toString();
            ps.setString(1, eventoUuid.toString());
            ps.setString(2, tipo.name());
            ps.setString(3, ocorridoEm.toString());
            ps.setString(4, payloadJson);
            ps.setString(5, agora);
            ps.setString(6, agora);
            ps.executeUpdate();
        }
    }

    public List<EventoOutbox> proximosPendentes(int limite) throws SQLException {
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement("""
                    SELECT id, evento_uuid, tipo, ocorrido_em, payload_json, tentativas
                    FROM sync_outbox
                    WHERE status = 'PENDENTE' AND proxima_tentativa_em <= ?
                    ORDER BY id
                    LIMIT ?;
                    """)) {
                ps.setString(1, Instant.now().toString());
                ps.setInt(2, limite);
                ResultSet rs = ps.executeQuery();
                List<EventoOutbox> eventos = new ArrayList<>();
                while (rs.next()) {
                    eventos.add(new EventoOutbox(
                            rs.getLong("id"),
                            UUID.fromString(rs.getString("evento_uuid")),
                            TipoEventoPdv.valueOf(rs.getString("tipo")),
                            Instant.parse(rs.getString("ocorrido_em")),
                            rs.getString("payload_json"),
                            rs.getInt("tentativas")));
                }
                return eventos;
            }
        }
    }

    public void marcarEnviado(long id, Long servidorId) throws SQLException {
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "UPDATE sync_outbox SET status = 'ENVIADO', servidor_id = ? WHERE id = ?;")) {
                if (servidorId == null) {
                    ps.setNull(1, java.sql.Types.INTEGER);
                } else {
                    ps.setLong(1, servidorId);
                }
                ps.setLong(2, id);
                ps.executeUpdate();
            }
        }
    }

    /** Falha de rede/servidor indisponível — tenta de novo depois, com backoff exponencial (5s, 10s, 20s... até 15min). */
    public void marcarErroTemporario(long id, String erro, int tentativaAtual) throws SQLException {
        long atrasoSegundos = Math.min(5L << Math.min(tentativaAtual, 8), 900L);
        Instant proximaTentativa = Instant.now().plusSeconds(atrasoSegundos);
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement("""
                    UPDATE sync_outbox
                    SET tentativas = tentativas + 1, ultimo_erro = ?, proxima_tentativa_em = ?
                    WHERE id = ?;
                    """)) {
                ps.setString(1, erro);
                ps.setString(2, proximaTentativa.toString());
                ps.setLong(3, id);
                ps.executeUpdate();
            }
        }
    }

    /** Rejeição de negócio (4xx do servidor) — não adianta tentar de novo sem intervenção humana. */
    public void marcarRejeitado(long id, String erro) throws SQLException {
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "UPDATE sync_outbox SET status = 'REJEITADO', ultimo_erro = ? WHERE id = ?;")) {
                ps.setString(1, erro);
                ps.setLong(2, id);
                ps.executeUpdate();
            }
        }
    }

    public int contarPendentes() throws SQLException {
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT COUNT(*) FROM sync_outbox WHERE status = 'PENDENTE';")) {
                ResultSet rs = ps.executeQuery();
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int contarRejeitados() throws SQLException {
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT COUNT(*) FROM sync_outbox WHERE status = 'REJEITADO';")) {
                ResultSet rs = ps.executeQuery();
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
