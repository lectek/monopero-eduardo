package br.com.lojagenerica.pdvclient.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalEstoqueCacheDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void upsertDepoisListarAtivosIgnoraInativos() throws Exception {
        try (LocalDb db = new LocalDb(tempDir.resolve("pdv-local.db"))) {
            LocalEstoqueCacheDao dao = new LocalEstoqueCacheDao(db);
            dao.upsert(new LocalEstoqueCache(1L, "Loja", "LOJA", true, true));
            dao.upsert(new LocalEstoqueCache(2L, "Depósito desativado", "DEPOSITO", false, false));

            assertThat(dao.listarAtivos()).extracting(LocalEstoqueCache::id).containsExactly(1L);
        }
    }
}
