package mysquare.core;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.sql.ResultSet;

public class ModifyProducts {

    private static <T> void runAsync(JComponent panel, JButton button, java.util.concurrent.Callable<T> task,
                                      java.util.function.Consumer<T> onSuccess, java.util.function.Consumer<Exception> onError) {
        button.setEnabled(false);
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                panel.setCursor(Cursor.getDefaultCursor());
                button.setEnabled(true);
                try {
                    onSuccess.accept(get());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException ee) {
                    Exception cause = ee.getCause() instanceof Exception ? (Exception) ee.getCause() : ee;
                    onError.accept(cause);
                }
            }
        }.execute();
    }

    public static JPanel getPanel() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(Theme.BACKGROUND);
        root.add(buildEditSection());
        return root;
    }

    /** Primary section: pick an existing product and edit every one of its fields together, or delete it. */
    private static JPanel buildEditSection() {
        JPanel section = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 8));
        section.setBackground(Theme.SURFACE);
        section.setBorder(Theme.sectionBorder("Editar ou excluir um produto existente"));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComboBox<ProductOption> productPicker = new JComboBox<ProductOption>(getProductKeys());
        JTextField nameField = new JTextField(12);
        JTextField colourField = new JTextField(8);
        JTextField weightField = new JTextField(8);
        JTextField qtyField = new JTextField(6);
        JTextField codeField = new JTextField(8);
        JTextField priceField = new JTextField(6);
        JTextField descriptionField = new JTextField(30);

        JPanel pickerRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        pickerRow.setOpaque(false);
        pickerRow.add(Theme.labeledField("Selecionar um produto", productPicker));
        pickerRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel fieldsRow1 = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 8));
        fieldsRow1.setOpaque(false);
        fieldsRow1.setAlignmentX(Component.CENTER_ALIGNMENT);
        fieldsRow1.add(Theme.labeledField("Produto", nameField));
        fieldsRow1.add(Theme.labeledField("Cor", colourField));
        fieldsRow1.add(Theme.labeledField("Peso", weightField));
        fieldsRow1.add(Theme.labeledField("Quantidade", qtyField));

        JPanel fieldsRow2 = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 8));
        fieldsRow2.setOpaque(false);
        fieldsRow2.setAlignmentX(Component.CENTER_ALIGNMENT);
        fieldsRow2.add(Theme.labeledField("Código", codeField));
        fieldsRow2.add(Theme.labeledField("Preço", priceField));
        fieldsRow2.add(Theme.labeledField("Descrição", descriptionField));

        JButton saveBtn = new JButton("Salvar alterações");
        saveBtn.setFont(Theme.FONT_BUTTON);
        saveBtn.setBackground(Theme.ACCENT);
        saveBtn.setForeground(Color.WHITE);
        JButton deleteBtn = new JButton("Excluir produto");
        deleteBtn.setFont(Theme.FONT_BUTTON);

        JPanel buttonsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 6));
        buttonsRow.setOpaque(false);
        buttonsRow.add(saveBtn);
        buttonsRow.add(deleteBtn);

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        buttonsRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(pickerRow);
        column.add(fieldsRow1);
        column.add(fieldsRow2);
        column.add(buttonsRow);
        section.add(column);

        Runnable loadSelected = () -> {
            Object selected = productPicker.getSelectedItem();
            if (!(selected instanceof ProductOption)) {
                return;
            }
            ProductOption option = (ProductOption) selected;
            try {
                ResultSet data = Db.fetchProduct(option.product, option.colour, option.weight);
                if (data.next()) {
                    nameField.setText(data.getString("pname"));
                    colourField.setText(data.getString("pclr"));
                    weightField.setText(data.getString("pwt"));
                    qtyField.setText(nullToEmpty(data.getString("pqt")));
                    codeField.setText(nullToEmpty(data.getString("pcode")));
                    descriptionField.setText(nullToEmpty(data.getString("pdesc")));
                    String priceValue = data.getString("pprice");
                    priceField.setText(priceValue == null ? "0" : priceValue);
                }
            } catch (Exception ignored) {
                // Leave fields as-is; the operator can retry by reselecting.
            }
        };
        productPicker.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                loadSelected.run();
            }
        });
        loadSelected.run();

        saveBtn.addActionListener(e -> {
            Object selected = productPicker.getSelectedItem();
            if (!(selected instanceof ProductOption)) {
                JOptionPane.showConfirmDialog(IMStart.frame, "Selecione um produto primeiro.", "AVISO", JOptionPane.WARNING_MESSAGE);
                return;
            }
            ProductOption option = (ProductOption) selected;
            String updatedName = nameField.getText().trim();
            String updatedColour = colourField.getText().trim();
            String updatedWeight = weightField.getText().trim();
            if (updatedName.isEmpty() || updatedColour.isEmpty() || updatedWeight.isEmpty()) {
                JOptionPane.showConfirmDialog(IMStart.frame, "Produto, cor e peso são obrigatórios.", "AVISO", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int updatedQty;
            double updatedPrice;
            try {
                updatedQty = Integer.parseInt(qtyField.getText().trim());
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(IMStart.frame, "Informe uma quantidade válida.", "AVISO", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                updatedPrice = parsePrice(priceField.getText());
            } catch (Exception err) {
                JOptionPane.showMessageDialog(IMStart.frame, "Preço inválido.", "AVISO", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String updatedCode = codeField.getText().trim();
            String updatedDescription = descriptionField.getText().trim();
            runAsync(section, saveBtn,
                    () -> {
                        Db.updateProduct(option.product, option.colour, option.weight,
                                updatedName, updatedColour, updatedWeight,
                                updatedCode, updatedDescription, updatedPrice, updatedQty);
                        return getProductKeys();
                    },
                    keys -> {
                        productPicker.setModel(new DefaultComboBoxModel<ProductOption>(keys));
                        selectMatching(productPicker, updatedName, updatedColour, updatedWeight);
                        JOptionPane.showMessageDialog(IMStart.frame, "Produto atualizado com sucesso.");
                    },
                    err -> JOptionPane.showConfirmDialog(IMStart.frame, "Não foi possível atualizar o produto.\nERRO:" + err.getMessage(), "AVISO", JOptionPane.WARNING_MESSAGE));
        });

        deleteBtn.addActionListener(e -> {
            Object selected = productPicker.getSelectedItem();
            if (!(selected instanceof ProductOption)) {
                JOptionPane.showConfirmDialog(IMStart.frame, "Selecione um produto primeiro.", "AVISO", JOptionPane.WARNING_MESSAGE);
                return;
            }
            ProductOption option = (ProductOption) selected;
            int confirm = JOptionPane.showConfirmDialog(IMStart.frame,
                    "Excluir " + option + "? Isso só remove do catálogo; o histórico de produção/despacho é mantido.",
                    "Confirmar exclusão", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            runAsync(section, deleteBtn,
                    () -> {
                        Db.deleteProduct(option.product, option.colour, option.weight);
                        return getProductKeys();
                    },
                    keys -> {
                        productPicker.setModel(new DefaultComboBoxModel<ProductOption>(keys));
                        loadSelected.run();
                    },
                    err -> JOptionPane.showConfirmDialog(IMStart.frame, "Não foi possível excluir o produto.\nERRO:" + err.getMessage(), "AVISO", JOptionPane.WARNING_MESSAGE));
        });

        return section;
    }

    private static ProductOption[] getProductKeys() {
        try {
            ResultSet data = Db.fetchProducts();
            java.util.ArrayList<ProductOption> keys = new java.util.ArrayList<ProductOption>();
            while (data.next()) {
                keys.add(new ProductOption(data.getString("pname"), data.getString("pclr"), data.getString("pwt")));
            }
            return keys.toArray(new ProductOption[keys.size()]);
        } catch (Exception e) {
            return new ProductOption[0];
        }
    }

    private static void selectMatching(JComboBox<ProductOption> box, String product, String colour, String weight) {
        for (int i = 0; i < box.getItemCount(); i++) {
            ProductOption option = box.getItemAt(i);
            if (option.product.equals(product) && option.colour.equals(colour) && option.weight.equals(weight)) {
                box.setSelectedIndex(i);
                return;
            }
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static double parsePrice(String value) throws Exception {
        String cleanValue = value.trim().replace(",", ".");
        if (cleanValue.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(cleanValue);
        } catch (NumberFormatException e) {
            throw new Exception("Preço inválido.");
        }
    }

    private static class ProductOption {
        private final String product;
        private final String colour;
        private final String weight;

        private ProductOption(String product, String colour, String weight) {
            this.product = product;
            this.colour = colour;
            this.weight = weight;
        }

        public String toString() {
            return product + " | " + colour + " | " + weight;
        }
    }
}
