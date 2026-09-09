package mysquare.core;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;

/** "Configurações" screen: lets the operator set the Mercado Pago Pix credentials from inside the app. */
public class Settings {

    public static JPanel getSettingsPanel() {
        JPanel root = buildRoot();
        return root;
    }

    private static JPanel buildRoot() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(Theme.BACKGROUND);
        root.add(buildPixSection());
        root.add(buildSaasSection());
        return root;
    }

    private static JPanel buildPixSection() {
        JPanel section = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 8));
        section.setBackground(Theme.SURFACE);
        section.setBorder(Theme.sectionBorder("Pagamento via Pix (Mercado Pago)"));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPasswordField tokenField = new JPasswordField(40);
        tokenField.setEchoChar('•');
        JTextField emailField = new JTextField(30);

        String currentToken = "";
        String currentEmail = "";
        try {
            HashMap<String, String> props = new Utility().getProperties();
            currentToken = props.getOrDefault("mpAccessToken", "");
            currentEmail = props.getOrDefault("mpPayerEmail", "");
        } catch (Exception ignored) {
            // Leave the fields blank; the operator can still type fresh values and save.
        }
        tokenField.setText(currentToken);
        emailField.setText(currentEmail);

        JCheckBox showToken = new JCheckBox("Mostrar");
        showToken.setOpaque(false);
        showToken.addActionListener(e -> tokenField.setEchoChar(showToken.isSelected() ? (char) 0 : '•'));

        JPanel tokenRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        tokenRow.setOpaque(false);
        tokenRow.add(Theme.labeledField("Access Token de produção", tokenField));
        tokenRow.add(showToken);

        JPanel emailRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 8));
        emailRow.setOpaque(false);
        emailRow.add(Theme.labeledField("E-mail padrão do pagador", emailField));

        JLabel statusLabel = new JLabel(currentToken.trim().isEmpty()
                ? "Pix está desativado (nenhum token configurado)."
                : "Pix está configurado.");
        statusLabel.setFont(Theme.FONT_LABEL);
        statusLabel.setForeground(currentToken.trim().isEmpty() ? Color.RED.darker() : Theme.ACCENT_DARK);

        JButton saveBtn = new JButton("Salvar");
        saveBtn.setFont(Theme.FONT_BUTTON);
        saveBtn.setBackground(Theme.ACCENT);
        saveBtn.setForeground(Color.WHITE);

        JPanel buttonsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 6));
        buttonsRow.setOpaque(false);
        buttonsRow.add(saveBtn);
        buttonsRow.add(statusLabel);

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        buttonsRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        tokenRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        emailRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(tokenRow);
        column.add(emailRow);
        column.add(buttonsRow);
        section.add(column);

        saveBtn.addActionListener(e -> {
            String token = new String(tokenField.getPassword()).trim();
            String email = emailField.getText().trim();
            saveBtn.setEnabled(false);
            section.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    new Utility().saveMercadoPagoCredentials(token, email);
                    return null;
                }

                @Override
                protected void done() {
                    section.setCursor(Cursor.getDefaultCursor());
                    saveBtn.setEnabled(true);
                    try {
                        get();
                        statusLabel.setText(token.isEmpty() ? "Pix está desativado (nenhum token configurado)." : "Pix está configurado.");
                        statusLabel.setForeground(token.isEmpty() ? Color.RED.darker() : Theme.ACCENT_DARK);
                        JOptionPane.showMessageDialog(IMStart.frame, "Configuração salva com sucesso.");
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(IMStart.frame,
                                "Não foi possível salvar a configuração.\n" + ex.getMessage(), "ERRO", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        return section;
    }

    /** Endereço da loja online (SaaS) — usado pelo botão "Loja Online" da tela inicial. */
    private static JPanel buildSaasSection() {
        JPanel section = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 8));
        section.setBackground(Theme.SURFACE);
        section.setBorder(Theme.sectionBorder("Loja Online (site)"));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField urlField = new JTextField(40);
        String currentUrl = "";
        try {
            currentUrl = new Utility().getProperties().getOrDefault("saasAdminUrl", "");
        } catch (Exception ignored) {
            // Campo fica em branco; a loja pode digitar e salvar mesmo assim.
        }
        urlField.setText(currentUrl);

        JLabel statusLabel = new JLabel(currentUrl.trim().isEmpty()
                ? "Endereço da loja online ainda não configurado."
                : "Endereço configurado.");
        statusLabel.setFont(Theme.FONT_LABEL);
        statusLabel.setForeground(currentUrl.trim().isEmpty() ? Color.RED.darker() : Theme.ACCENT_DARK);

        JButton saveBtn = new JButton("Salvar");
        saveBtn.setFont(Theme.FONT_BUTTON);
        saveBtn.setBackground(Theme.ACCENT);
        saveBtn.setForeground(Color.WHITE);

        JPanel urlRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        urlRow.setOpaque(false);
        urlRow.add(Theme.labeledField("Endereço do site (ex.: https://minimercadinho.com.br/admin)", urlField));

        JPanel buttonsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 6));
        buttonsRow.setOpaque(false);
        buttonsRow.add(saveBtn);
        buttonsRow.add(statusLabel);

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        urlRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonsRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(urlRow);
        column.add(buttonsRow);
        section.add(column);

        saveBtn.addActionListener(e -> {
            String url = urlField.getText().trim();
            saveBtn.setEnabled(false);
            section.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    new Utility().saveSaasAdminUrl(url);
                    return null;
                }

                @Override
                protected void done() {
                    section.setCursor(Cursor.getDefaultCursor());
                    saveBtn.setEnabled(true);
                    try {
                        get();
                        statusLabel.setText(url.isEmpty() ? "Endereço da loja online ainda não configurado." : "Endereço configurado.");
                        statusLabel.setForeground(url.isEmpty() ? Color.RED.darker() : Theme.ACCENT_DARK);
                        JOptionPane.showMessageDialog(IMStart.frame, "Configuração salva com sucesso.");
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(IMStart.frame,
                                "Não foi possível salvar a configuração.\n" + ex.getMessage(), "ERRO", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        return section;
    }
}
