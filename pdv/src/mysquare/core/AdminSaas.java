package mysquare.core;

import org.mindrot.jbcrypt.BCrypt;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * Cria/atualiza os acessos ao site (SaaS) — de propósito, o único lugar que
 * faz isso é aqui, no IMS. O SaaS nunca se auto-cadastra, então só quem tem
 * acesso a este programa (o dono da loja) consegue criar ou trocar acesso.
 *
 * Cobre dois papéis na mesma tela: "Admin" (painel administrativo do site) e
 * "Motoboy" (área de entregas no celular do entregador, com % de comissão
 * sobre o frete de cada entrega).
 */
public class AdminSaas {

    private static JList<String> adminList;
    private static DefaultListModel<String> adminListModel;
    private static JTextField nomeField;
    private static JTextField emailField;
    private static JPasswordField senhaField;
    private static JPasswordField confirmarField;
    private static JRadioButton roleAdminRadio;
    private static JRadioButton roleMotoboyRadio;
    private static JTextField comissaoField;
    private static JLabel statusLabel;

    public static JPanel getPanel() {
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        adminListModel = new DefaultListModel<String>();
        adminList = new JList<String>(adminListModel);
        adminList.setFont(Theme.FONT_LABEL);
        JScrollPane listScroll = new JScrollPane(adminList);
        listScroll.setBorder(Theme.sectionBorder("Acessos atuais"));
        listScroll.setPreferredSize(new Dimension(320, 300));

        JButton refreshBtn = new JButton("Atualizar lista");
        refreshBtn.setFont(Theme.FONT_LABEL);
        refreshBtn.addActionListener(e -> carregarAdmins());

        JPanel leftPanel = new JPanel(new BorderLayout(0, 6));
        leftPanel.setOpaque(false);
        leftPanel.add(listScroll, BorderLayout.CENTER);
        leftPanel.add(refreshBtn, BorderLayout.SOUTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(Theme.sectionBorder("Criar / atualizar acesso"));

        nomeField = new JTextField();
        emailField = new JTextField();
        senhaField = new JPasswordField();
        confirmarField = new JPasswordField();
        comissaoField = new JTextField("70");

        form.add(campo("Nome", nomeField));
        form.add(Box.createVerticalStrut(10));
        form.add(campo("E-mail (login)", emailField));
        form.add(Box.createVerticalStrut(10));
        form.add(campo("Senha", senhaField));
        form.add(Box.createVerticalStrut(10));
        form.add(campo("Confirmar senha", confirmarField));
        form.add(Box.createVerticalStrut(16));

        JLabel tipoLabel = new JLabel("Tipo de acesso");
        tipoLabel.setFont(Theme.FONT_LABEL);
        tipoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        roleAdminRadio = new JRadioButton("Admin (painel administrativo)", true);
        roleMotoboyRadio = new JRadioButton("Motoboy (entregas pelo celular)");
        roleAdminRadio.setFont(Theme.FONT_LABEL);
        roleMotoboyRadio.setFont(Theme.FONT_LABEL);
        roleAdminRadio.setOpaque(false);
        roleMotoboyRadio.setOpaque(false);
        roleAdminRadio.setAlignmentX(Component.LEFT_ALIGNMENT);
        roleMotoboyRadio.setAlignmentX(Component.LEFT_ALIGNMENT);
        ButtonGroup roleGroup = new ButtonGroup();
        roleGroup.add(roleAdminRadio);
        roleGroup.add(roleMotoboyRadio);

        JPanel comissaoPanel = campo("% de comissão do motoboy sobre o frete de cada entrega", comissaoField);
        comissaoPanel.setVisible(false);

        roleAdminRadio.addActionListener(e -> comissaoPanel.setVisible(false));
        roleMotoboyRadio.addActionListener(e -> comissaoPanel.setVisible(true));

        form.add(tipoLabel);
        form.add(roleAdminRadio);
        form.add(roleMotoboyRadio);
        form.add(Box.createVerticalStrut(10));
        form.add(comissaoPanel);
        form.add(Box.createVerticalStrut(16));

        statusLabel = new JLabel(" ");
        statusLabel.setFont(Theme.FONT_LABEL);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton salvarBtn = new JButton("Salvar acesso");
        salvarBtn.setFont(Theme.FONT_BUTTON);
        salvarBtn.setBackground(Theme.ACCENT);
        salvarBtn.setForeground(Color.WHITE);
        salvarBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        salvarBtn.addActionListener(e -> salvar(salvarBtn));

        JLabel aviso = new JLabel("<html><body style='width:280px'>Se o e-mail já existir, os dados são "
                + "atualizados (inclusive o tipo de acesso). Se não existir, um novo acesso é criado.</body></html>");
        aviso.setFont(Theme.FONT_LABEL);
        aviso.setForeground(Theme.TEXT_MUTED);
        aviso.setAlignmentX(Component.LEFT_ALIGNMENT);
        aviso.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        form.add(aviso);
        form.add(salvarBtn);
        form.add(Box.createVerticalStrut(8));
        form.add(statusLabel);

        root.add(leftPanel, BorderLayout.WEST);
        root.add(new JScrollPane(form), BorderLayout.CENTER);

        carregarAdmins();
        return root;
    }

    private static JPanel campo(String rotulo, JTextField field) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(360, 70));

        JLabel label = new JLabel(rotulo);
        label.setFont(Theme.FONT_LABEL);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        field.setFont(Theme.FONT_FIELD);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(360, 36));

        p.add(label);
        p.add(field);
        return p;
    }

    private static void carregarAdmins() {
        new SwingWorker<ArrayList<Db.AdminSaasAccount>, Void>() {
            @Override
            protected ArrayList<Db.AdminSaasAccount> doInBackground() {
                return Db.fetchAdminAccounts();
            }

            @Override
            protected void done() {
                ArrayList<Db.AdminSaasAccount> contas;
                try {
                    contas = get();
                } catch (Exception ex) {
                    contas = new ArrayList<Db.AdminSaasAccount>();
                }
                adminListModel.clear();
                if (contas.isEmpty()) {
                    adminListModel.addElement("(nenhum acesso criado ainda)");
                }
                for (Db.AdminSaasAccount conta : contas) {
                    String rotulo = conta.email + " — " + conta.role;
                    if ("MOTOBOY".equals(conta.role) && conta.percentualComissao != null) {
                        rotulo += " (" + conta.percentualComissao + "%)";
                    }
                    adminListModel.addElement(rotulo);
                }
            }
        }.execute();
    }

    private static void salvar(JButton salvarBtn) {
        String nome = nomeField.getText().trim();
        String email = emailField.getText().trim().toLowerCase();
        char[] senha = senhaField.getPassword();
        char[] confirmar = confirmarField.getPassword();
        boolean isMotoboy = roleMotoboyRadio.isSelected();

        if (nome.isEmpty() || email.isEmpty() || senha.length == 0) {
            statusLabel.setForeground(Theme.ACCENT);
            statusLabel.setText("Preencha nome, e-mail e senha.");
            return;
        }
        if (senha.length < 6) {
            statusLabel.setForeground(Theme.ACCENT);
            statusLabel.setText("A senha precisa ter pelo menos 6 caracteres.");
            return;
        }
        if (!new String(senha).equals(new String(confirmar))) {
            statusLabel.setForeground(Theme.ACCENT);
            statusLabel.setText("As senhas não coincidem.");
            return;
        }

        Double percentualComissao = null;
        if (isMotoboy) {
            try {
                percentualComissao = Double.parseDouble(comissaoField.getText().trim().replace(",", "."));
            } catch (NumberFormatException ex) {
                statusLabel.setForeground(Theme.ACCENT);
                statusLabel.setText("Percentual de comissão inválido.");
                return;
            }
        }
        final Double comissaoFinal = percentualComissao;
        final String role = isMotoboy ? "MOTOBOY" : "ADMIN";

        String senhaHash = BCrypt.hashpw(new String(senha), BCrypt.gensalt());
        java.util.Arrays.fill(senha, '\0');
        java.util.Arrays.fill(confirmar, '\0');

        salvarBtn.setEnabled(false);
        statusLabel.setForeground(Theme.TEXT_MUTED);
        statusLabel.setText("Salvando...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                Db.salvarAdminSaas(nome, email, senhaHash, role, comissaoFinal);
                return null;
            }

            @Override
            protected void done() {
                salvarBtn.setEnabled(true);
                try {
                    get();
                    statusLabel.setForeground(Theme.ACCENT_DARK);
                    statusLabel.setText("Acesso salvo para " + email + ".");
                    senhaField.setText("");
                    confirmarField.setText("");
                    carregarAdmins();
                } catch (Exception ex) {
                    statusLabel.setForeground(Theme.ACCENT);
                    statusLabel.setText("Não foi possível salvar. O site (SaaS) já rodou nesta máquina alguma vez?");
                    JOptionPane.showMessageDialog(IMStart.frame,
                            "Não foi possível salvar o acesso.\nERRO:" + ex.getMessage(),
                            "ERRO", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
