package br.com.lojagenerica.pdvclient.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Prova a invariante central do PDV offline (ver docs/CONTEXTO.md): uma
 * venda nunca existe sem seu evento de outbox correspondente, gravados na
 * mesma transação SQLite.
 */
class VendaLocalDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void registrarVendaGravaEspelhoLocalEEnfileiraEventoComOMesmoUuid() throws Exception {
        try (LocalDb db = new LocalDb(tempDir.resolve("pdv-local.db"))) {
            OutboxDao outboxDao = new OutboxDao(db);
            VendaLocalDao vendaLocalDao = new VendaLocalDao(db, outboxDao);

            UUID vendaUuid = vendaLocalDao.registrarVenda(1L, null, 7L, null,
                    List.of(new ItemVendaLocal(10L, "Parafuso", 3.0, 2L, 10.0, null)),
                    List.of(new PagamentoVendaLocal(5L, 30.0, 30.0, 0.0)));

            List<EventoOutbox> pendentes = outboxDao.proximosPendentes(10);
            assertThat(pendentes).hasSize(1);
            EventoOutbox evento = pendentes.get(0);
            assertThat(evento.eventoUuid()).isEqualTo(vendaUuid);
            assertThat(evento.tipo().name()).isEqualTo("VENDA_REGISTRADA");

            JSONObject payload = new JSONObject(evento.payloadJson());
            assertThat(payload.getLong("localEstoqueId")).isEqualTo(1L);
            assertThat(payload.getLong("usuarioId")).isEqualTo(7L);
            assertThat(payload.getJSONArray("itens").getJSONObject(0).getLong("produtoId")).isEqualTo(10L);
            assertThat(payload.getJSONArray("pagamentos").getJSONObject(0).getLong("formaPagamentoId")).isEqualTo(5L);
        }
    }

    @Test
    void registrarCancelamentoMarcaVendaLocalEEnfileiraEvento() throws Exception {
        try (LocalDb db = new LocalDb(tempDir.resolve("pdv-local.db"))) {
            OutboxDao outboxDao = new OutboxDao(db);
            VendaLocalDao vendaLocalDao = new VendaLocalDao(db, outboxDao);

            UUID vendaUuid = vendaLocalDao.registrarVenda(1L, null, null, null,
                    List.of(new ItemVendaLocal(10L, "Parafuso", 1.0, 2L, 10.0, null)),
                    List.of(new PagamentoVendaLocal(5L, 10.0, 10.0, 0.0)));
            // simula que o evento de venda já foi enviado, sobrando só o de cancelamento pra inspecionar
            outboxDao.marcarEnviado(outboxDao.proximosPendentes(10).get(0).id(), 99L);

            vendaLocalDao.registrarCancelamento(vendaUuid, "cliente desistiu", 7L);

            List<EventoOutbox> pendentes = outboxDao.proximosPendentes(10);
            assertThat(pendentes).hasSize(1);
            assertThat(pendentes.get(0).tipo().name()).isEqualTo("VENDA_CANCELADA");
            JSONObject payload = new JSONObject(pendentes.get(0).payloadJson());
            assertThat(payload.getString("vendaUuid")).isEqualTo(vendaUuid.toString());
            assertThat(payload.getString("motivo")).isEqualTo("cliente desistiu");

            try (var stmt = db.connection().prepareStatement("SELECT status FROM venda_local WHERE venda_uuid = ?;")) {
                stmt.setString(1, vendaUuid.toString());
                var rs = stmt.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("CANCELADA");
            }
        }
    }
}
