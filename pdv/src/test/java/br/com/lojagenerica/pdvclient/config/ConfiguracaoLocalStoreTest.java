package br.com.lojagenerica.pdvclient.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfiguracaoLocalStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void carregarSemArquivoRetornaConfiguracaoVaziaNaoPareada() {
        ConfiguracaoLocalStore store = new ConfiguracaoLocalStore(tempDir.resolve("config.properties"));

        ConfiguracaoLocal configuracao = store.carregar();

        assertThat(configuracao.pareado()).isFalse();
    }

    @Test
    void salvarDepoisCarregarPreservaTodosOsCampos() {
        Path arquivo = tempDir.resolve("sub/config.properties");
        ConfiguracaoLocalStore store = new ConfiguracaoLocalStore(arquivo);
        Instant pareadoEm = Instant.parse("2026-01-01T10:00:00Z");
        ConfiguracaoLocal original = new ConfiguracaoLocal(
                "https://loja1.lojagenerica.com.br", "empresa_001.segredoXYZ", "Caixa 1",
                "Loja do Eduardo", pareadoEm, "APP_USR-token", "loja@exemplo.com");

        store.salvar(original);
        ConfiguracaoLocal recarregada = store.carregar();

        assertThat(recarregada).isEqualTo(original);
        assertThat(recarregada.pareado()).isTrue();
    }
}
