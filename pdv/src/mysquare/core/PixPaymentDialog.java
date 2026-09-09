package mysquare.core;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

/** Modal dialog: shows a Pix QR code and polls Mercado Pago until it's paid or the operator cancels. */
public class PixPaymentDialog extends JDialog {

    public enum Resultado { APROVADO, CANCELADO }

    private static final int POLL_INTERVAL_MS = 3000;
    private static final int TIMEOUT_MS = 5 * 60 * 1000;
    private static final int QR_DISPLAY_SIZE = 260;

    private final MercadoPagoPixClient client;
    private final String paymentId;
    private final JLabel statusLabel;
    private Timer pollTimer;
    private long startedAt;
    private Resultado resultado = Resultado.CANCELADO;

    public PixPaymentDialog(Frame owner, MercadoPagoPixClient client, MercadoPagoPixClient.PixCharge charge) {
        super(owner, "Pagamento via Pix", true);
        this.client = client;
        this.paymentId = charge.paymentId;

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        content.setBackground(Theme.SURFACE);

        JLabel qrLabel = new JLabel();
        qrLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        BufferedImage qrImage = decodeQrCode(charge.qrCodeBase64);
        if (qrImage != null) {
            // Mercado Pago returns the QR code at a high native resolution (often 500px+ square);
            // shown as-is it dwarfs the rest of the dialog, so it's scaled down to a fixed, still
            // easily-scannable size.
            Image scaled = qrImage.getScaledInstance(QR_DISPLAY_SIZE, QR_DISPLAY_SIZE, Image.SCALE_SMOOTH);
            qrLabel.setIcon(new ImageIcon(scaled));
        } else {
            qrLabel.setText("Não foi possível carregar a imagem do QR code.");
        }

        JTextArea copiaCola = new JTextArea(charge.qrCodeCopiaCola == null ? "" : charge.qrCodeCopiaCola);
        copiaCola.setEditable(false);
        copiaCola.setLineWrap(true);
        copiaCola.setWrapStyleWord(true);
        copiaCola.setFont(Theme.FONT_LABEL);
        JScrollPane copiaColaScroll = new JScrollPane(copiaCola);
        copiaColaScroll.setAlignmentX(Component.CENTER_ALIGNMENT);
        copiaColaScroll.setPreferredSize(new Dimension(360, 70));
        copiaColaScroll.setMaximumSize(new Dimension(360, 70));

        JButton copyBtn = new JButton("Copiar código Pix");
        copyBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        copyBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(charge.qrCodeCopiaCola == null ? "" : charge.qrCodeCopiaCola), null);
            JOptionPane.showMessageDialog(this, "Código copiado.");
        });

        statusLabel = new JLabel("Aguardando pagamento...");
        statusLabel.setFont(Theme.FONT_LABEL);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setForeground(Theme.ACCENT_DARK);

        JButton cancelBtn = new JButton("Cancelar");
        cancelBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        cancelBtn.addActionListener(e -> {
            resultado = Resultado.CANCELADO;
            fechar();
        });

        content.add(qrLabel);
        content.add(Box.createVerticalStrut(12));
        content.add(copiaColaScroll);
        content.add(Box.createVerticalStrut(8));
        content.add(copyBtn);
        content.add(Box.createVerticalStrut(16));
        content.add(statusLabel);
        content.add(Box.createVerticalStrut(12));
        content.add(cancelBtn);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
        // The X button acts exactly like "Cancelar" rather than doing nothing — a dialog with no way
        // to close it is an app-breaking bug the moment its content is ever taller than the screen.
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                resultado = Resultado.CANCELADO;
                fechar();
            }
        });
    }

    /** Shows the dialog and blocks (it's modal) until the payment is approved or the operator cancels/times out. */
    public Resultado aguardarPagamento() {
        startedAt = System.currentTimeMillis();
        pollTimer = new Timer(POLL_INTERVAL_MS, e -> verificarStatus());
        pollTimer.setInitialDelay(POLL_INTERVAL_MS);
        pollTimer.start();
        setVisible(true);
        return resultado;
    }

    private void verificarStatus() {
        if (System.currentTimeMillis() - startedAt > TIMEOUT_MS) {
            statusLabel.setText("Tempo esgotado. Cancele e tente novamente.");
            pollTimer.stop();
            return;
        }
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return client.consultarStatus(paymentId);
            }

            @Override
            protected void done() {
                try {
                    String status = get();
                    if ("approved".equals(status)) {
                        resultado = Resultado.APROVADO;
                        fechar();
                    } else if ("cancelled".equals(status) || "rejected".equals(status)) {
                        statusLabel.setText("Pagamento não aprovado (" + status + ").");
                        pollTimer.stop();
                    }
                } catch (Exception ex) {
                    statusLabel.setText("Erro ao consultar pagamento: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void fechar() {
        if (pollTimer != null) {
            pollTimer.stop();
        }
        setVisible(false);
        dispose();
    }

    private static BufferedImage decodeQrCode(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        } catch (Exception e) {
            return null;
        }
    }
}
