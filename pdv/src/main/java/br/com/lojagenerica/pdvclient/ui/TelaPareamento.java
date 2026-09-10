package br.com.lojagenerica.pdvclient.ui;

import br.com.lojagenerica.pdvclient.config.ConfiguracaoLocal;
import br.com.lojagenerica.pdvclient.config.ConfiguracaoLocalStore;
import br.com.lojagenerica.pdvclient.sync.ApiClient;
import java.awt.BorderLayout;
import java.awt.Component;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.ZoneId;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Único lugar onde o terminal é pareado: o admin cria o terminal no painel
 * web (permissão {@code TERMINAL_GERENCIAR}), recebe uma chave de API que
 * só aparece UMA vez, e cola aqui. "Testar conexão" chama
 * {@code GET /api/v1/pdv/sync/pull} de verdade antes de deixar salvar —
 * uma chave errada nunca fica salva sem confirmação de que funciona.
 *
 * <p>Não verificado visualmente nesta sessão (sem display disponível) —
 * compilado e revisado, mas precisa de um primeiro uso manual antes de
 * confiar no fluxo completo (ver docs/ROADMAP.md, Fase D).
 */
public final class TelaPareamento extends JPanel {

    private final ConfiguracaoLocalStore store;
    private final JTextField campoServidorUrl = new JTextField(30);
    private final JTextField campoApiKey = new JTextField(40);
    private final JTextField campoNomeTerminal = new JTextField(20);
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton botaoTestar = new JButton("Testar conexão");
    private final JButton botaoSalvar = new JButton("Salvar pareamento");

    private boolean ultimoTesteOk = false;

    public TelaPareamento(ConfiguracaoLocalStore store) {
        super(new BorderLayout());
        this.store = store;
        setBackground(Theme.BACKGROUND);

        JPanel card = Theme.card("Pareamento do terminal");
        card.add(Theme.labeledField("Endereço do servidor (ex.: https://loja1.lojagenerica.com.br)", campoServidorUrl));
        card.add(Theme.labeledField("Chave de API do terminal (gerada no painel web, uma única vez)", campoApiKey));
        card.add(Theme.labeledField("Nome deste terminal (só pra você identificar, ex.: \"Caixa 1\")", campoNomeTerminal));

        botaoTestar.setAlignmentX(Component.LEFT_ALIGNMENT);
        botaoTestar.addActionListener(e -> testarConexao());

        botaoSalvar.setAlignmentX(Component.LEFT_ALIGNMENT);
        botaoSalvar.setEnabled(false);
        botaoSalvar.addActionListener(e -> salvar());

        statusLabel.setFont(Theme.FONT_LABEL);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel botoes = new JPanel();
        botoes.setOpaque(false);
        botoes.setLayout(new BoxLayout(botoes, BoxLayout.X_AXIS));
        botoes.setAlignmentX(Component.LEFT_ALIGNMENT);
        botoes.add(botaoTestar);
        botoes.add(javax.swing.Box.createHorizontalStrut(10));
        botoes.add(botaoSalvar);

        card.add(javax.swing.Box.createVerticalStrut(10));
        card.add(botoes);
        card.add(javax.swing.Box.createVerticalStrut(10));
        card.add(statusLabel);

        add(card, BorderLayout.NORTH);
        carregarEstadoAtual();
    }

    private void carregarEstadoAtual() {
        ConfiguracaoLocal configuracao = store.carregar();
        campoServidorUrl.setText(configuracao.servidorBaseUrl());
        campoApiKey.setText(configuracao.terminalApiKey());
        campoNomeTerminal.setText(configuracao.terminalNome());
        if (configuracao.pareado()) {
            String quando = configuracao.pareadoEm() == null ? "" : formatarData(configuracao.pareadoEm());
            statusLabel.setForeground(Theme.SUCCESS);
            statusLabel.setText("Já pareado" + (quando.isEmpty() ? "." : " em " + quando + "."));
        }
    }

    private void testarConexao() {
        String url = campoServidorUrl.getText().trim();
        String apiKey = campoApiKey.getText().trim();
        if (url.isEmpty() || apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Preencha o endereço do servidor e a chave de API.",
                    "Campos obrigatórios", JOptionPane.WARNING_MESSAGE);
            return;
        }
        botaoTestar.setEnabled(false);
        botaoSalvar.setEnabled(false);
        statusLabel.setForeground(Theme.TEXT_MUTED);
        statusLabel.setText("Testando conexão...");

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return new ApiClient(url, apiKey).testarConexao();
            }

            @Override
            protected void done() {
                boolean ok;
                try {
                    ok = get();
                } catch (Exception e) {
                    ok = false;
                }
                ultimoTesteOk = ok;
                botaoTestar.setEnabled(true);
                if (ok) {
                    statusLabel.setForeground(Theme.SUCCESS);
                    statusLabel.setText("Conexão OK — pode salvar.");
                    botaoSalvar.setEnabled(true);
                } else {
                    statusLabel.setForeground(Theme.ERROR);
                    statusLabel.setText("Não foi possível conectar. Confira o endereço e a chave.");
                }
            }
        }.execute();
    }

    private void salvar() {
        if (!ultimoTesteOk) {
            return;
        }
        ConfiguracaoLocal atual = store.carregar();
        ConfiguracaoLocal novo = new ConfiguracaoLocal(
                campoServidorUrl.getText().trim(),
                campoApiKey.getText().trim(),
                campoNomeTerminal.getText().trim(),
                atual.empresaNome(),
                Instant.now(),
                atual.mpAccessToken(),
                atual.mpPayerEmail());
        store.salvar(novo);
        statusLabel.setForeground(Theme.SUCCESS);
        statusLabel.setText("Pareamento salvo em " + formatarData(novo.pareadoEm()) + ".");
        JOptionPane.showMessageDialog(this,
                "Pareamento salvo. Reinicie o programa para começar a sincronizar.",
                "Pareamento", JOptionPane.INFORMATION_MESSAGE);
    }

    private String formatarData(Instant instant) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    /** Abre esta tela sozinha numa janela — útil pra rodar o pareamento antes do resto do programa existir. */
    public static void abrirStandalone() {
        SwingUtilities.invokeLater(() -> {
            Theme.applyLookAndFeel();
            javax.swing.JFrame frame = new javax.swing.JFrame("Pareamento do terminal — Loja Genérica PDV");
            frame.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
            frame.getContentPane().add(new TelaPareamento(new ConfiguracaoLocalStore()));
            frame.setSize(700, 420);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
