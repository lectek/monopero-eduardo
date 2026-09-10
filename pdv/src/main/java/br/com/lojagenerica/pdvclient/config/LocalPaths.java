package br.com.lojagenerica.pdvclient.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Onde o terminal guarda seu estado local. Diferente do {@code rbp.db} do
 * legado (fixo em {@code C:/ims_files}, uma pasta por instalação): a pasta
 * agora é a padrão de cada usuário do Windows ({@code %APPDATA%}), porque
 * um "terminal" deixou de ser sinônimo de "a única instalação da loja" —
 * qualquer máquina pareada precisa do seu próprio banco local sem colidir
 * com outra.
 */
public final class LocalPaths {

    private LocalPaths() {
    }

    public static Path baseDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path base;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            base = Paths.get(appData != null ? appData : System.getProperty("user.home"), "lojagenerica");
        } else {
            base = Paths.get(System.getProperty("user.home"), ".lojagenerica");
        }
        return base;
    }

    public static Path databaseFile() {
        return baseDir().resolve("pdv-local.db");
    }

    public static Path configFile() {
        return baseDir().resolve("config.properties");
    }
}
