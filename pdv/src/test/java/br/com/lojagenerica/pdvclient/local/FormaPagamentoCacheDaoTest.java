package br.com.lojagenerica.pdvclient.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FormaPagamentoCacheDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void upsertDepoisListarAtivasIgnoraInativas() throws Exception {
        try (LocalDb db = new LocalDb(tempDir.resolve("pdv-local.db"))) {
            FormaPagamentoCacheDao dao = new FormaPagamentoCacheDao(db);
            dao.upsert(new FormaPagamentoCache(1L, "Dinheiro", "DINHEIRO", true, true));
            dao.upsert(new FormaPagamentoCache(2L, "Cartão desativado", "CARTAO_CREDITO", true, false));

            assertThat(dao.listarAtivas()).extracting(FormaPagamentoCache::id).containsExactly(1L);
        }
    }

    @Test
    void upsertMesmoIdAtualizaEmVezDeDuplicar() throws Exception {
        try (LocalDb db = new LocalDb(tempDir.resolve("pdv-local.db"))) {
            FormaPagamentoCacheDao dao = new FormaPagamentoCacheDao(db);
            dao.upsert(new FormaPagamentoCache(1L, "Dinheiro", "DINHEIRO", true, true));
            dao.upsert(new FormaPagamentoCache(1L, "Dinheiro (renomeado)", "DINHEIRO", true, true));

            assertThat(dao.listarAtivas()).hasSize(1);
            assertThat(dao.listarAtivas().get(0).nome()).isEqualTo("Dinheiro (renomeado)");
        }
    }
}
