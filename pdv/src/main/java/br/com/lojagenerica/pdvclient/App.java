package br.com.lojagenerica.pdvclient;

import br.com.lojagenerica.pdvclient.config.ConfiguracaoLocal;
import br.com.lojagenerica.pdvclient.config.ConfiguracaoLocalStore;
import br.com.lojagenerica.pdvclient.config.LocalPaths;
import br.com.lojagenerica.pdvclient.local.FormaPagamentoCacheDao;
import br.com.lojagenerica.pdvclient.local.LocalDb;
import br.com.lojagenerica.pdvclient.local.LocalEstoqueCacheDao;
import br.com.lojagenerica.pdvclient.local.OutboxDao;
import br.com.lojagenerica.pdvclient.local.ProdutoCacheDao;
import br.com.lojagenerica.pdvclient.local.SyncCursorDao;
import br.com.lojagenerica.pdvclient.local.VendaLocalDao;
import br.com.lojagenerica.pdvclient.sync.ApiClient;
import br.com.lojagenerica.pdvclient.sync.SyncScheduler;
import br.com.lojagenerica.pdvclient.ui.TelaPareamento;
import br.com.lojagenerica.pdvclient.ui.Theme;
import br.com.lojagenerica.pdvclient.ui.VendaScreen;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Ponto de entrada do cliente PDV novo — ainda não é o {@code mainClass}
 * do jar empacotado (esse continua em {@code mysquare.core.IMStart} até
 * o corte final, ver docs/ROADMAP.md, Fase D). Roda com
 * {@code mvn exec:java -Dexec.mainClass=br.com.lojagenerica.pdvclient.App}
 * ou direto pela IDE enquanto as telas restantes (login, ajuste de
 * estoque, recibo) ainda não existem.
 *
 * <p>Não verificado visualmente nesta sessão (sem display disponível).
 */
public final class App {

    public static void main(String[] args) {
        Theme.applyLookAndFeel();
        ConfiguracaoLocalStore store = new ConfiguracaoLocalStore();
        ConfiguracaoLocal configuracao = store.carregar();

        if (!configuracao.pareado()) {
            TelaPareamento.abrirStandalone();
            return;
        }

        SwingUtilities.invokeLater(() -> abrirTelaDeVenda(store, configuracao));
    }

    private static void abrirTelaDeVenda(ConfiguracaoLocalStore store, ConfiguracaoLocal configuracao) {
        try {
            LocalDb db = new LocalDb(LocalPaths.databaseFile());
            ProdutoCacheDao produtoCacheDao = new ProdutoCacheDao(db);
            FormaPagamentoCacheDao formaPagamentoCacheDao = new FormaPagamentoCacheDao(db);
            LocalEstoqueCacheDao localEstoqueCacheDao = new LocalEstoqueCacheDao(db);
            SyncCursorDao syncCursorDao = new SyncCursorDao(db);
            OutboxDao outboxDao = new OutboxDao(db);
            VendaLocalDao vendaLocalDao = new VendaLocalDao(db, outboxDao);

            ApiClient apiClient = new ApiClient(configuracao.servidorBaseUrl(), configuracao.terminalApiKey());
            SyncScheduler scheduler = new SyncScheduler(apiClient, outboxDao, produtoCacheDao,
                    formaPagamentoCacheDao, localEstoqueCacheDao, syncCursorDao);
            scheduler.iniciar();
            Runtime.getRuntime().addShutdownHook(new Thread(scheduler::parar));

            JFrame frame = new JFrame("Venda — " + emptyToDash(configuracao.terminalNome()));
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.getContentPane().add(new VendaScreen(produtoCacheDao, formaPagamentoCacheDao, localEstoqueCacheDao, vendaLocalDao));
            frame.setSize(1200, 800);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "Não foi possível iniciar o terminal.\nERRO: " + e.getMessage(),
                    "Erro ao iniciar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String emptyToDash(String nome) {
        return nome == null || nome.isBlank() ? "—" : nome;
    }
}
