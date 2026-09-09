package mysquare.core;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Landing screen: quick-access buttons to every other screen, plus a gross-revenue summary
 *  the operator can flip between today / this month / this year / all time. */
public class Home {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    public static JPanel getHomePanel() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BACKGROUND);

        JLabel title = buildLogoLabel();
        title.setBorder(BorderFactory.createEmptyBorder(20, 0, 4, 0));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Sistema de Gerenciamento de Estoque", SwingConstants.CENTER);
        subtitle.setFont(Theme.FONT_LABEL);
        subtitle.setForeground(Theme.TEXT_MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(title);
        top.add(subtitle);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(buildRevenuePanel());
        content.add(Box.createVerticalStrut(16));
        content.add(buildNavPanel());

        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(content), BorderLayout.CENTER);
        return root;
    }

    /** Store logo, scaled to a fixed display width; falls back to a plain text title if it can't be loaded. */
    private static JLabel buildLogoLabel() {
        final int DISPLAY_WIDTH = 700;
        try {
            BufferedImage original = ImageIO.read(Home.class.getResourceAsStream("logo.png"));
            int height = Math.round(DISPLAY_WIDTH * (original.getHeight() / (float) original.getWidth()));
            Image scaled = original.getScaledInstance(DISPLAY_WIDTH, height, Image.SCALE_SMOOTH);
            JLabel label = new JLabel(new ImageIcon(scaled), SwingConstants.CENTER);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            return label;
        } catch (IOException | NullPointerException ex) {
            JLabel fallback = new JLabel("Mini Mercadinho Rota o Melhor da Zona Sul", SwingConstants.CENTER);
            fallback.setFont(new Font("Georgia", Font.BOLD | Font.ITALIC, 34));
            fallback.setForeground(Theme.ACCENT);
            return fallback;
        }
    }

    private static JPanel buildNavPanel() {
        JPanel nav = new JPanel(new GridLayout(0, 3, 16, 16));
        nav.setOpaque(false);
        nav.setBorder(Theme.sectionBorder("Acesso rápido"));
        nav.add(navButton("Nova venda", IMStart::showVenda));
        nav.add(navButton("Produção", IMStart::showProducao));
        nav.add(navButton("Despacho", IMStart::showDespacho));
        nav.add(navButton("Estoque", IMStart::showEstoque));
        nav.add(navButton("Calendário de vendas", IMStart::showCalendario));
        nav.add(navButton("Vendas por dia", IMStart::showVendasPorDia));
        nav.add(navButton("Modificar produtos", IMStart::showModificarProdutos));
        nav.add(navButton("Configurações (Pix)", IMStart::showConfiguracoes));
        nav.add(navButton("WhatsApp Web", IMStart::abrirWhatsAppWeb));
        nav.add(navButton("Loja Online", IMStart::abrirLojaOnline));
        nav.add(navButton("Acessos do site", IMStart::showAdminSaas));
        return nav;
    }

    private static JButton navButton(String label, Runnable action) {
        JButton b = new JButton("<html><center>" + label + "</center></html>");
        b.setFont(Theme.FONT_BUTTON);
        b.setBackground(Theme.SURFACE);
        b.setForeground(Theme.ACCENT_DARK);
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(220, 90));
        b.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        b.addActionListener(e -> action.run());
        return b;
    }

    private static JPanel buildRevenuePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Theme.SURFACE);
        panel.setBorder(Theme.sectionBorder("Faturamento bruto"));

        JLabel valueLabel = new JLabel("R$ 0,00", SwingConstants.CENTER);
        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 44));
        valueLabel.setForeground(Theme.ACCENT);
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Cached once per fetch; the period toggle buttons below just pick which slot to show,
        // so switching period is instant and doesn't hit the database again.
        double[] cache = {0, 0, 0, 0}; // hoje, mes, ano, total
        int[] periodoSelecionado = {0};

        JToggleButton bHoje = new JToggleButton("Hoje", true);
        JToggleButton bMes = new JToggleButton("Mês");
        JToggleButton bAno = new JToggleButton("Ano");
        JToggleButton bTotal = new JToggleButton("Desde o início");
        ButtonGroup group = new ButtonGroup();
        group.add(bHoje);
        group.add(bMes);
        group.add(bAno);
        group.add(bTotal);
        JToggleButton[] toggles = {bHoje, bMes, bAno, bTotal};
        for (JToggleButton t : toggles) {
            t.setFont(Theme.FONT_LABEL);
        }

        Runnable[] atualizarLabel = new Runnable[1];
        atualizarLabel[0] = () -> valueLabel.setText(String.format(PT_BR, "R$ %.2f", cache[periodoSelecionado[0]]));

        for (int i = 0; i < toggles.length; i++) {
            final int periodo = i;
            toggles[i].addActionListener(e -> {
                periodoSelecionado[0] = periodo;
                atualizarLabel[0].run();
            });
        }

        JButton refreshBtn = new JButton("Atualizar");
        Runnable[] buscarResumo = new Runnable[1];
        buscarResumo[0] = () -> {
            refreshBtn.setEnabled(false);
            new SwingWorker<Db.RevenueSummary, Void>() {
                @Override
                protected Db.RevenueSummary doInBackground() throws Exception {
                    return Db.fetchRevenueSummary();
                }

                @Override
                protected void done() {
                    refreshBtn.setEnabled(true);
                    try {
                        Db.RevenueSummary resumo = get();
                        cache[0] = resumo.hoje;
                        cache[1] = resumo.mes;
                        cache[2] = resumo.ano;
                        cache[3] = resumo.total;
                        atualizarLabel[0].run();
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(IMStart.frame,
                                "Não foi possível calcular o faturamento.\nERRO:" + ex.getMessage(), "ERRO", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        };
        refreshBtn.addActionListener(e -> buscarResumo[0].run());

        JPanel toggleRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        toggleRow.setOpaque(false);
        for (JToggleButton t : toggles) {
            toggleRow.add(t);
        }
        toggleRow.add(refreshBtn);

        panel.add(valueLabel);
        panel.add(toggleRow);

        buscarResumo[0].run();
        return panel;
    }
}
