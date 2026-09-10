package br.com.lojagenerica.pdvclient.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;

/**
 * Lê/grava {@code config.properties} — mesma ideia do {@code Utility} do
 * legado, mas o caminho vem de {@link LocalPaths} (não mais fixo em
 * {@code C:/ims_files}) e as chaves cobrem o pareamento do terminal, não só
 * o caminho do SQLite (que agora é sempre {@code pdv-local.db} ao lado do
 * próprio arquivo de config — não precisa mais ser configurável).
 */
public final class ConfiguracaoLocalStore {

    private static final String CHAVE_SERVIDOR_BASE_URL = "SERVIDOR_BASE_URL";
    private static final String CHAVE_TERMINAL_API_KEY = "TERMINAL_API_KEY";
    private static final String CHAVE_TERMINAL_NOME = "TERMINAL_NOME";
    private static final String CHAVE_EMPRESA_NOME = "EMPRESA_NOME";
    private static final String CHAVE_PAREADO_EM = "PAREADO_EM";
    private static final String CHAVE_MP_ACCESS_TOKEN = "MP_ACCESS_TOKEN";
    private static final String CHAVE_MP_PAYER_EMAIL = "MP_PAYER_EMAIL_PADRAO";

    private final Path arquivo;

    public ConfiguracaoLocalStore() {
        this(LocalPaths.configFile());
    }

    public ConfiguracaoLocalStore(Path arquivo) {
        this.arquivo = arquivo;
    }

    public ConfiguracaoLocal carregar() {
        Properties props = new Properties();
        if (Files.exists(arquivo)) {
            try (InputStream in = Files.newInputStream(arquivo)) {
                props.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Não foi possível ler " + arquivo, e);
            }
        }
        String pareadoEmTexto = props.getProperty(CHAVE_PAREADO_EM);
        return new ConfiguracaoLocal(
                props.getProperty(CHAVE_SERVIDOR_BASE_URL, ""),
                props.getProperty(CHAVE_TERMINAL_API_KEY, ""),
                props.getProperty(CHAVE_TERMINAL_NOME, ""),
                props.getProperty(CHAVE_EMPRESA_NOME, ""),
                pareadoEmTexto == null || pareadoEmTexto.isBlank() ? null : Instant.parse(pareadoEmTexto),
                props.getProperty(CHAVE_MP_ACCESS_TOKEN, ""),
                props.getProperty(CHAVE_MP_PAYER_EMAIL, ""));
    }

    public void salvar(ConfiguracaoLocal configuracao) {
        Properties props = new Properties();
        props.setProperty(CHAVE_SERVIDOR_BASE_URL, nullToEmpty(configuracao.servidorBaseUrl()));
        props.setProperty(CHAVE_TERMINAL_API_KEY, nullToEmpty(configuracao.terminalApiKey()));
        props.setProperty(CHAVE_TERMINAL_NOME, nullToEmpty(configuracao.terminalNome()));
        props.setProperty(CHAVE_EMPRESA_NOME, nullToEmpty(configuracao.empresaNome()));
        props.setProperty(CHAVE_PAREADO_EM, configuracao.pareadoEm() == null ? "" : configuracao.pareadoEm().toString());
        props.setProperty(CHAVE_MP_ACCESS_TOKEN, nullToEmpty(configuracao.mpAccessToken()));
        props.setProperty(CHAVE_MP_PAYER_EMAIL, nullToEmpty(configuracao.mpPayerEmail()));
        try {
            Files.createDirectories(arquivo.getParent());
            try (OutputStream out = Files.newOutputStream(arquivo)) {
                props.store(out, "Configuração local do terminal PDV — não versionar (contém a chave de API do terminal).");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível gravar " + arquivo, e);
        }
    }

    private static String nullToEmpty(String valor) {
        return valor == null ? "" : valor;
    }
}
