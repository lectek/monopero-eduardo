package br.com.lojagenerica.pdvclient.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.lojagenerica.pdvclient.shared.TipoEventoPdv;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutboxDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void enfileirarRejeitaUuidDuplicado() throws Exception {
        try (LocalDb db = new LocalDb(tempDir.resolve("pdv-local.db"))) {
            OutboxDao dao = new OutboxDao(db);
            UUID uuid = UUID.randomUUID();
            dao.enfileirar(uuid, TipoEventoPdv.VENDA_REGISTRADA, Instant.now(), "{}");

            assertThatThrownBy(() -> dao.enfileirar(uuid, TipoEventoPdv.VENDA_REGISTRADA, Instant.now(), "{}"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    void proximosPendentesRespeitaBackoffAntesDaProximaTentativa() throws Exception {
        try (LocalDb db = new LocalDb(tempDir.resolve("pdv-local.db"))) {
            OutboxDao dao = new OutboxDao(db);
            UUID uuid = UUID.randomUUID();
            dao.enfileirar(uuid, TipoEventoPdv.VENDA_REGISTRADA, Instant.now(), "{}");

            List<EventoOutbox> antesDoErro = dao.proximosPendentes(10);
            assertThat(antesDoErro).hasSize(1);

            dao.marcarErroTemporario(antesDoErro.get(0).id(), "timeout", 0);

            // backoff da 1ª tentativa é de 5s — não deve reaparecer imediatamente
            List<EventoOutbox> logoDepois = dao.proximosPendentes(10);
            assertThat(logoDepois).isEmpty();
        }
    }

    @Test
    void marcarEnviadoRemoveDosPendentes() throws Exception {
        try (LocalDb db = new LocalDb(tempDir.resolve("pdv-local.db"))) {
            OutboxDao dao = new OutboxDao(db);
            UUID uuid = UUID.randomUUID();
            dao.enfileirar(uuid, TipoEventoPdv.VENDA_REGISTRADA, Instant.now(), "{}");
            EventoOutbox evento = dao.proximosPendentes(10).get(0);

            dao.marcarEnviado(evento.id(), 42L);

            assertThat(dao.proximosPendentes(10)).isEmpty();
            assertThat(dao.contarPendentes()).isZero();
        }
    }

    @Test
    void marcarRejeitadoContaSeparadoDePendentes() throws Exception {
        try (LocalDb db = new LocalDb(tempDir.resolve("pdv-local.db"))) {
            OutboxDao dao = new OutboxDao(db);
            UUID uuid = UUID.randomUUID();
            dao.enfileirar(uuid, TipoEventoPdv.VENDA_REGISTRADA, Instant.now(), "{}");
            EventoOutbox evento = dao.proximosPendentes(10).get(0);

            dao.marcarRejeitado(evento.id(), "produto inexistente");

            assertThat(dao.contarPendentes()).isZero();
            assertThat(dao.contarRejeitados()).isEqualTo(1);
        }
    }
}
