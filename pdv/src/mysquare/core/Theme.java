package mysquare.core;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

final class Theme {

    // Paleta: vermelho paixão + azul marinho + branco bebê.
    static final Color BACKGROUND = new Color(0xFD, 0xF6, 0xF3);
    static final Color SURFACE = new Color(0xFF, 0xFF, 0xFF);
    static final Color BORDER = new Color(0xE6, 0xDA, 0xD4);
    static final Color ACCENT = new Color(0xC8, 0x10, 0x2E);
    static final Color ACCENT_DARK = new Color(0x0F, 0x1B, 0x33);
    static final Color TEXT = new Color(0x1B, 0x2A, 0x44);
    static final Color TEXT_MUTED = new Color(0x51, 0x60, 0x7A);
    static final Color TABLE_HEADER_BG = new Color(0xF2, 0xE3, 0xDE);
    static final Color TABLE_STRIPE = new Color(0xFA, 0xF3, 0xEF);

    static final Font FONT_LABEL = new Font("SansSerif", Font.PLAIN, 18);
    static final Font FONT_FIELD = new Font("SansSerif", Font.PLAIN, 19);
    static final Font FONT_BUTTON = new Font("SansSerif", Font.BOLD, 24);
    static final Font FONT_TABLE = new Font("SansSerif", Font.PLAIN, 19);
    static final Font FONT_TABLE_HEADER = new Font("SansSerif", Font.BOLD, 18);
    static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 20);

    static final int TABLE_ROW_HEIGHT = 34;

    private static final String INSTAGRAM_HANDLE = "@lektec.tech";
    private static final String INSTAGRAM_URL = "https://instagram.com/lektec.tech";

    private Theme() {
    }

    /**
     * Applies Nimbus (bundled with the JDK) if available and makes every button ~30% bigger
     * than the platform default; silently keeps the platform default otherwise.
     */
    static void applyLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // Keep whatever look and feel Swing already picked.
        }
        UIManager.put("Button.font", FONT_BUTTON);
    }

    /**
     * Shared page footer: a "Como usar" help button (shows helpBody in a dialog titled
     * helpTitle) on the left, and the developer's Instagram link on the right.
     */
    static JPanel footer(String helpTitle, String helpBody) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BACKGROUND);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));

        JButton howToUse = new JButton("Como usar");
        howToUse.setFont(FONT_LABEL);
        howToUse.addActionListener(e -> JOptionPane.showMessageDialog(null, helpBody, helpTitle, JOptionPane.INFORMATION_MESSAGE));

        JLabel instagram = new JLabel(INSTAGRAM_HANDLE);
        instagram.setFont(FONT_LABEL);
        instagram.setForeground(ACCENT);
        instagram.setHorizontalAlignment(SwingConstants.RIGHT);
        instagram.setAlignmentX(Component.RIGHT_ALIGNMENT);
        instagram.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        instagram.setToolTipText(INSTAGRAM_URL);
        instagram.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI(INSTAGRAM_URL));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, INSTAGRAM_URL, "Instagram", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });

        JPanel credit = new JPanel();
        credit.setOpaque(false);
        credit.setLayout(new BoxLayout(credit, BoxLayout.Y_AXIS));
        JLabel smallLogo = smallLogoLabel();
        if (smallLogo != null) {
            smallLogo.setAlignmentX(Component.RIGHT_ALIGNMENT);
            credit.add(smallLogo);
        }
        credit.add(instagram);

        bar.add(howToUse, BorderLayout.WEST);
        bar.add(credit, BorderLayout.EAST);
        return bar;
    }

    /** Small store-logo thumbnail shown above the developer credit in every screen's footer; null if it can't be loaded. */
    private static JLabel smallLogoLabel() {
        final int DISPLAY_WIDTH = 120;
        try {
            java.awt.image.BufferedImage original = javax.imageio.ImageIO.read(Theme.class.getResourceAsStream("logo.png"));
            int height = Math.round(DISPLAY_WIDTH * (original.getHeight() / (float) original.getWidth()));
            Image scaled = original.getScaledInstance(DISPLAY_WIDTH, height, Image.SCALE_SMOOTH);
            JLabel label = new JLabel(new ImageIcon(scaled));
            label.setHorizontalAlignment(SwingConstants.RIGHT);
            return label;
        } catch (Exception ex) {
            return null;
        }
    }

    /** A label stacked above a field, used to give the add/dispatch forms a clearer caption per input. */
    static JPanel labeledField(String label, JComponent field) {
        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setOpaque(false);
        JLabel caption = new JLabel(label);
        caption.setFont(FONT_LABEL);
        caption.setForeground(TEXT_MUTED);
        caption.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        field.setFont(FONT_FIELD);
        field.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        wrap.add(caption);
        wrap.add(field);
        wrap.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return wrap;
    }

    /** Titled section border used to group a form inside a panel. */
    static Border sectionBorder(String title) {
        Border line = BorderFactory.createLineBorder(BORDER);
        Border titled = BorderFactory.createTitledBorder(line, title);
        ((javax.swing.border.TitledBorder) titled).setTitleFont(FONT_TITLE);
        ((javax.swing.border.TitledBorder) titled).setTitleColor(ACCENT_DARK);
        return BorderFactory.createCompoundBorder(titled, BorderFactory.createEmptyBorder(10, 16, 16, 16));
    }
}
