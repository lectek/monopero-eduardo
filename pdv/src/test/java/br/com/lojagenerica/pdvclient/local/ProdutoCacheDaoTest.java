package br.com.lojagenerica.pdvclient.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProdutoCacheDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void upsertInsereDepoisAtualizaMesmoId() throws Exception {
        try (LocalDb db = new LocalDb(tempDir.resolve("pdv-local.db"))) {
            ProdutoCacheDao dao = new ProdutoCacheDao(db);

            dao.upsert(new ProdutoCache(1L, "Parafuso 3/4", "COD1", 10.0, 2L, true, "ATIVO", Instant.parse("2026-01-01T00:00:00Z")));
            dao.upsert(new ProdutoCache(1L, "Parafuso 3/4 (novo preço)", "COD1", 12.5, 2L, true, "ATIVO", Instant.parse("2026-01-02T00:00:00Z")));

            List<ProdutoCache> encontrados = dao.buscarPorNomeOuCodigo("Parafuso");
            assertThat(encontrados).hasSize(1);
            assertThat(encontrados.get(0).nome()).isEqualTo("Parafuso 3/4 (novo preço)");
            assertThat(encontrados.get(0).precoVenda()).isEqualTo(12.5);
        }
    }

    @Test
    void buscarPorNomeOuCodigoEncontraPorCodigoExato() throws Exception {
        try (LocalDb db = new LocalDb(tempDir.resolve("pdv-local.db"))) {
            ProdutoCacheDao dao = new ProdutoCacheDao(db);
            dao.upsert(new ProdutoCache(2L, "Martelo", "MRT-01", 45.0, 2L, true, "ATIVO", Instant.now()));

            assertThat(dao.buscarPorNomeOuCodigo("MRT-01")).extracting(ProdutoCache::id).containsExactly(2L);
        }
    }

    @Test
    void buscarPorIdRetornaNullQuandoNaoExiste() throws Exception {
        try (LocalDb db = new LocalDb(tempDir.resolve("pdv-local.db"))) {
            ProdutoCacheDao dao = new ProdutoCacheDao(db);
            assertThat(dao.buscarPorId(999L)).isNull();
        }
    }
}
