package mysquare.core;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/** A checkout screen: scan or add items to a running sale, see the total, then confirm (deducts stock) or cancel. */
public class Sale {

    private static class CartLine {
        final String product;
        final String colour;
        final String weight;
        final int qty;
        final double unitPrice;

        CartLine(String product, String colour, String weight, int qty, double unitPrice) {
            this.product = product;
            this.colour = colour;
            this.weight = weight;
            this.qty = qty;
            this.unitPrice = unitPrice;
        }

        double subtotal() {
            return qty * unitPrice;
        }
    }

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    public static JPanel getSalePanel() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BACKGROUND);

        List<CartLine> cart = new ArrayList<CartLine>();

        DefaultTableModel cartModel = new DefaultTableModel(new Object[]{"Produto", "Cor", "Peso", "Qtd", "Preço unit.", "Subtotal"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable cartTable = new JTable(cartModel);
        cartTable.setRowHeight(Theme.TABLE_ROW_HEIGHT);
        cartTable.setFont(Theme.FONT_TABLE);
        cartTable.getTableHeader().setFont(Theme.FONT_TABLE_HEADER);
        cartTable.getTableHeader().setBackground(Theme.TABLE_HEADER_BG);
        cartTable.setSelectionBackground(Theme.ACCENT);
        cartTable.setSelectionForeground(Color.WHITE);
        cartTable.setGridColor(Theme.BORDER);

        Utility util = new Utility();
        JComboBox<String> productBox = new JComboBox<String>(util.getProductList());
        JComboBox<String> colourBox = new JComboBox<String>();
        JComboBox<String> weightBox = new JComboBox<String>();
        JTextField qtyField = new JTextField(5);
        JTextField barcodeField = new JTextField(14);

        // Colour and weight only ever offer combinations this product is actually stocked in.
        Runnable reloadWeights = () -> {
            String product = (String) productBox.getSelectedItem();
            String colour = (String) colourBox.getSelectedItem();
            ArrayList<String> weights = product == null ? new ArrayList<String>() : Db.fetchWeightsForProduct(product, colour);
            weightBox.setModel(new DefaultComboBoxModel<String>(weights.toArray(new String[weights.size()])));
        };
        Runnable reloadColours = () -> {
            String product = (String) productBox.getSelectedItem();
            ArrayList<String> colours = product == null ? new ArrayList<String>() : Db.fetchColoursForProduct(product);
            colourBox.setModel(new DefaultComboBoxModel<String>(colours.toArray(new String[colours.size()])));
            reloadWeights.run();
        };
        productBox.addItemListener(e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                reloadColours.run();
            }
        });
        colourBox.addItemListener(e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                reloadWeights.run();
            }
        });
        reloadColours.run();

        JLabel totalLabel = new JLabel("Total: 0,00");
        totalLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        totalLabel.setForeground(Theme.ACCENT_DARK);

        Runnable recalcTotal = () -> {
            double total = 0;
            for (CartLine line : cart) {
                total += line.subtotal();
            }
            totalLabel.setText(String.format(PT_BR, "Total: %.2f", total));
        };

        JButton addBtn = new JButton("Adicionar à venda");
        addBtn.setFont(Theme.FONT_BUTTON);
        JButton removeLineBtn = new JButton("Remover linha selecionada");
        JButton confirmBtn = new JButton("Confirmar venda");
        confirmBtn.setFont(Theme.FONT_BUTTON);
        confirmBtn.setBackground(Theme.ACCENT);
        confirmBtn.setForeground(Color.WHITE);
        JButton cancelBtn = new JButton("Cancelar venda");

        // addToCart() at the bottom of this class is shared by the manual "Adicionar à venda" button and the
        // barcode scanner below: it checks live stock, appends a cart line and refreshes the total.

        barcodeField.addActionListener(e -> {
            String code = barcodeField.getText().trim();
            if (code.isEmpty()) {
                return;
            }
            barcodeField.setEnabled(false);
            new SwingWorker<Object[], Void>() {
                @Override
                protected Object[] doInBackground() throws Exception {
                    ResultSet rs = Db.fetchProductByCode(code);
                    if (!rs.next()) {
                        throw new Exception("Nenhum produto cadastrado com este código.");
                    }
                    return new Object[]{rs.getString("pname"), rs.getString("pclr"), rs.getString("pwt")};
                }

                @Override
                protected void done() {
                    barcodeField.setEnabled(true);
                    try {
                        Object[] found = get();
                        String product = (String) found[0];
                        String colour = (String) found[1];
                        String weight = (String) found[2];
                        productBox.setSelectedItem(product);
                        reloadColours.run();
                        colourBox.setSelectedItem(colour);
                        reloadWeights.run();
                        weightBox.setSelectedItem(weight);
                        // A scan always means "one unit, right now" — no extra click needed.
                        addToCart(product, colour, weight, 1, cart, cartModel, recalcTotal, root, () -> {}, () -> {});
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(IMStart.frame,
                                ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage(),
                                "Código não encontrado", JOptionPane.WARNING_MESSAGE);
                    }
                    barcodeField.setText("");
                    barcodeField.requestFocusInWindow();
                }
            }.execute();
        });

        addBtn.addActionListener(e -> {
            String product = (String) productBox.getSelectedItem();
            String colour = (String) colourBox.getSelectedItem();
            String weight = (String) weightBox.getSelectedItem();
            if (product == null || colour == null || weight == null) {
                JOptionPane.showMessageDialog(IMStart.frame, "Selecione um produto, cor e peso.", "AVISO", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int qty;
            try {
                qty = Integer.parseInt(qtyField.getText().trim());
                if (qty <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(IMStart.frame, "Informe uma quantidade válida.", "AVISO", JOptionPane.WARNING_MESSAGE);
                return;
            }
            addBtn.setEnabled(false);
            addToCart(product, colour, weight, qty, cart, cartModel, recalcTotal, root,
                    () -> addBtn.setEnabled(true),
                    () -> qtyField.setText(""));
        });

        removeLineBtn.addActionListener(e -> {
            int row = cartTable.getSelectedRow();
            if (row < 0) {
                return;
            }
            cart.remove(row);
            cartModel.removeRow(row);
            recalcTotal.run();
        });

        cancelBtn.addActionListener(e -> {
            cart.clear();
            cartModel.setRowCount(0);
            recalcTotal.run();
        });

        confirmBtn.addActionListener(e -> {
            if (cart.isEmpty()) {
                JOptionPane.showMessageDialog(IMStart.frame, "Adicione pelo menos um item primeiro.", "AVISO", JOptionPane.WARNING_MESSAGE);
                return;
            }
            double total = 0;
            for (CartLine line : cart) {
                total += line.subtotal();
            }
            String totalFormatado = String.format(PT_BR, "%.2f", total);

            Object[] opcoes = {"Dinheiro", "Pix", "Cancelar"};
            int escolha = JOptionPane.showOptionDialog(IMStart.frame,
                    "Confirmar venda de " + cart.size() + " item(ns) no valor total de " + totalFormatado + ".\nForma de pagamento:",
                    "Confirmar venda", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opcoes, opcoes[0]);
            if (escolha != 0 && escolha != 1) {
                return;
            }

            List<CartLine> toSell = new ArrayList<CartLine>(cart);
            double totalFinal = total;

            if (escolha == 0) {
                Double valorRecebido = perguntarValorRecebido(total);
                if (valorRecebido == null) {
                    return;
                }
                double troco = valorRecebido - total;
                String pagamentoResumo = String.format(PT_BR, "Pagamento: Dinheiro | Recebido: %.2f | Troco: %.2f", valorRecebido, troco);
                finalizarVenda(toSell, totalFinal, pagamentoResumo, troco, confirmBtn, root, cart, cartModel, recalcTotal);
                return;
            }

            // escolha == 1: Pix. MP_ACCESS_TOKEN vazio (padrão) mantém a opção visível mas desativada,
            // em vez de escondê-la — evita confundir o operador sobre por que "Pix" sumiu do menu.
            HashMap<String, String> props;
            try {
                props = new Utility().getProperties();
            } catch (Exception ex) {
                props = new HashMap<String, String>();
            }
            String accessToken = props.get("mpAccessToken");
            if (accessToken == null || accessToken.trim().isEmpty()) {
                JOptionPane.showMessageDialog(IMStart.frame,
                        "Pix não configurado. Defina MP_ACCESS_TOKEN em config.properties.",
                        "AVISO", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String payerEmail = props.get("mpPayerEmail");
            MercadoPagoPixClient mpClient = new MercadoPagoPixClient(accessToken, payerEmail);

            confirmBtn.setEnabled(false);
            root.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            new SwingWorker<MercadoPagoPixClient.PixCharge, Void>() {
                @Override
                protected MercadoPagoPixClient.PixCharge doInBackground() throws Exception {
                    return mpClient.criarCobranca(totalFinal, "Venda IMS - " + toSell.size() + " item(ns)");
                }

                @Override
                protected void done() {
                    root.setCursor(Cursor.getDefaultCursor());
                    confirmBtn.setEnabled(true);
                    try {
                        MercadoPagoPixClient.PixCharge cobranca = get();
                        PixPaymentDialog dialog = new PixPaymentDialog(IMStart.frame, mpClient, cobranca);
                        if (dialog.aguardarPagamento() == PixPaymentDialog.Resultado.APROVADO) {
                            finalizarVenda(toSell, totalFinal, "Pagamento: Pix", null, confirmBtn, root, cart, cartModel, recalcTotal);
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(IMStart.frame, "Não foi possível gerar o Pix.\n" + ex.getMessage(), "ERRO", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        JPanel formRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 8));
        formRow.setOpaque(false);
        formRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        formRow.add(Theme.labeledField("Código de barras", barcodeField));
        formRow.add(Theme.labeledField("Produto", productBox));
        formRow.add(Theme.labeledField("Cor", colourBox));
        formRow.add(Theme.labeledField("Peso", weightBox));
        formRow.add(Theme.labeledField("Quantidade", qtyField));
        formRow.add(addBtn);

        JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 8));
        actionsRow.setOpaque(false);
        actionsRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        actionsRow.add(removeLineBtn);
        actionsRow.add(totalLabel);
        actionsRow.add(confirmBtn);
        actionsRow.add(cancelBtn);

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBackground(Theme.SURFACE);
        bottom.setBorder(Theme.sectionBorder("Nova venda"));
        bottom.add(formRow);
        bottom.add(actionsRow);

        root.add(new JScrollPane(cartTable), BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        root.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && root.isShowing()) {
                barcodeField.requestFocusInWindow();
            }
        });

        return root;
    }

    /**
     * Deducts stock, prints the receipt (with the payment-method line when given) and clears the cart.
     * {@code troco} is shown in the confirmation dialog when the sale was paid in cash; null otherwise.
     */
    private static void finalizarVenda(List<CartLine> toSell, double totalFinal, String paymentSummary, Double troco,
                                        JButton confirmBtn, JPanel root, List<CartLine> cart,
                                        DefaultTableModel cartModel, Runnable recalcTotal) {
        confirmBtn.setEnabled(false);
        root.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                List<Db.SaleItem> items = new ArrayList<Db.SaleItem>();
                for (CartLine line : toSell) {
                    items.add(new Db.SaleItem(line.product, line.colour, line.weight, line.qty, line.unitPrice));
                }
                Db.sellProducts(items);
                return null;
            }

            @Override
            protected void done() {
                root.setCursor(Cursor.getDefaultCursor());
                confirmBtn.setEnabled(true);
                try {
                    get();
                    List<Receipt.Line> receiptLines = new ArrayList<Receipt.Line>();
                    for (CartLine line : toSell) {
                        receiptLines.add(new Receipt.Line(line.product, line.colour, line.weight, line.qty, line.unitPrice));
                    }
                    cart.clear();
                    cartModel.setRowCount(0);
                    recalcTotal.run();
                    Receipt.print(receiptLines, totalFinal, paymentSummary);
                    String mensagem = "Venda confirmada. Estoque atualizado.";
                    if (troco != null) {
                        mensagem += "\nTroco: " + String.format(PT_BR, "%.2f", troco);
                    }
                    JOptionPane.showMessageDialog(IMStart.frame, mensagem);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(IMStart.frame, "Não foi possível confirmar a venda.\n" + ex.getMessage(), "ERRO", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Asks the operator how much cash the customer handed over, re-asking on an invalid or
     * insufficient amount. Returns null if the operator cancels (the sale itself is untouched).
     */
    private static Double perguntarValorRecebido(double total) {
        while (true) {
            String input = JOptionPane.showInputDialog(IMStart.frame,
                    "Valor recebido do cliente (total da venda: " + String.format(PT_BR, "%.2f", total) + "):",
                    "Pagamento em dinheiro", JOptionPane.QUESTION_MESSAGE);
            if (input == null) {
                return null;
            }
            double valor;
            try {
                valor = Double.parseDouble(input.trim().replace(",", "."));
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(IMStart.frame, "Valor inválido.", "AVISO", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (valor < total) {
                JOptionPane.showMessageDialog(IMStart.frame,
                        "Valor insuficiente. Faltam " + String.format(PT_BR, "%.2f", total - valor) + ".",
                        "AVISO", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            return valor;
        }
    }

    /**
     * Confirms the line still fits in current stock, then appends it to the cart and refreshes the total.
     * {@code always} runs on both success and failure (typically re-enabling the triggering control);
     * {@code onSuccess} runs only once the line is actually added (e.g. to clear an input field).
     */
    private static void addToCart(String product, String colour, String weight, int qty,
                                   List<CartLine> cart, DefaultTableModel cartModel, Runnable recalcTotal,
                                   JPanel root, Runnable always, Runnable onSuccess) {
        root.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                ResultSet rs = Db.fetchProduct(product, colour, weight);
                if (!rs.next()) {
                    throw new Exception("Produto não encontrado.");
                }
                int stock = Integer.parseInt(rs.getString("pqt"));
                String priceStr = rs.getString("pprice");
                double price = priceStr == null || priceStr.isEmpty() ? 0 : Double.parseDouble(priceStr);
                return new Object[]{stock, price};
            }

            @Override
            protected void done() {
                root.setCursor(Cursor.getDefaultCursor());
                always.run();
                try {
                    Object[] result = get();
                    int stock = (Integer) result[0];
                    double price = (Double) result[1];

                    int alreadyInCart = 0;
                    for (CartLine line : cart) {
                        if (line.product.equals(product) && line.colour.equals(colour) && line.weight.equals(weight)) {
                            alreadyInCart += line.qty;
                        }
                    }
                    if (alreadyInCart + qty > stock) {
                        JOptionPane.showMessageDialog(IMStart.frame,
                                "Restam apenas " + (stock - alreadyInCart) + " em estoque.", "AVISO", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    CartLine line = new CartLine(product, colour, weight, qty, price);
                    cart.add(line);
                    cartModel.addRow(new Object[]{product, colour, weight, qty,
                            String.format(PT_BR, "%.2f", price), String.format(PT_BR, "%.2f", line.subtotal())});
                    recalcTotal.run();
                    onSuccess.run();
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(IMStart.frame, "Não foi possível adicionar o item.\n" + msg, "ERRO", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
