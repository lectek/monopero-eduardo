package br.com.lojagenerica.pdvclient.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

/**
 * Mesma paleta de {@code mysquare.core.Theme} (legado) — duplicada, não
 * compartilhada, porque aquela classe é pacote-privada e vai sumir junto
 * com o resto de {@code mysquare.core} conforme cada tela for reescrita
 * (ver docs/ROADMAP.md, Fase D). Evita acoplar o pacote novo a código que
 * está sendo substituído.
 */
public final class Theme {

    public static final Color BACKGROUND = new Color(0xFD, 0xF6, 0xF3);
    public static final Color SURFACE = new Color(0xFF, 0xFF, 0xFF);
    public static final Color BORDER = new Color(0xE6, 0xDA, 0xD4);
    public static final Color ACCENT = new Color(0xC8, 0x10, 0x2E);
    public static final Color ACCENT_DARK = new Color(0x0F, 0x1B, 0x33);
    public static final Color TEXT = new Color(0x1B, 0x2A, 0x44);
    public static final Color TEXT_MUTED = new Color(0x51, 0x60, 0x7A);
    public static final Color SUCCESS = new Color(0x1E, 0x7A, 0x34);
    public static final Color ERROR = new Color(0xB0, 0x0E, 0x28);

    public static final Font FONT_LABEL = new Font("SansSerif", Font.PLAIN, 18);
    public static final Font FONT_FIELD = new Font("SansSerif", Font.PLAIN, 19);
    public static final Font FONT_BUTTON = new Font("SansSerif", Font.BOLD, 22);
    public static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 20);

    private Theme() {
    }

    public static void applyLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        UIManager.put("Button.font", FONT_BUTTON);
    }

    public static JPanel labeledField(String label, JComponent field) {
        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setOpaque(false);
        JLabel caption = new JLabel(label);
        caption.setFont(FONT_LABEL);
        caption.setForeground(TEXT_MUTED);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setFont(FONT_FIELD);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.add(caption);
        wrap.add(field);
        wrap.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return wrap;
    }

    public static Border sectionBorder(String title) {
        Border line = BorderFactory.createLineBorder(BORDER);
        Border titled = BorderFactory.createTitledBorder(line, title);
        ((TitledBorder) titled).setTitleFont(FONT_TITLE);
        ((TitledBorder) titled).setTitleColor(ACCENT_DARK);
        return BorderFactory.createCompoundBorder(titled, BorderFactory.createEmptyBorder(10, 16, 16, 16));
    }

    public static JPanel card(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(SURFACE);
        panel.setBorder(sectionBorder(title));
        return panel;
    }

    public static JPanel row(JComponent... components) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        if (components.length > 0) {
            row.add(components[0], BorderLayout.WEST);
        }
        if (components.length > 1) {
            row.add(components[1], BorderLayout.CENTER);
        }
        return row;
    }
}
