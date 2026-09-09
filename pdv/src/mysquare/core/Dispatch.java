package mysquare.core;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.ResultSet;

public class Dispatch {

    public static DefaultTableModel model;

    public static JTable getDispatchView() throws Exception{
        model = new DefaultTableModel();
        JTable table = new JTable(model);
        model.addColumn("DESPACHADO EM");
        model.addColumn("PRODUTO");
        model.addColumn("COR");
        model.addColumn("PESO");
        model.addColumn("QUANTIDADE");

        ResultSet data = null;
        try {
            data = Db.fetchData("sold_records");
            while(data.next()) {
                model.addRow(new Object[]{data.getString(1), data.getString(2), data.getString(3), data.getString(4), data.getString(5)});
            }
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
        table.setGridColor(Theme.BORDER);
        table.setRowHeight(Theme.TABLE_ROW_HEIGHT);
        table.setFont(Theme.FONT_TABLE);
        table.getTableHeader().setFont(Theme.FONT_TABLE_HEADER);
        table.getTableHeader().setBackground(Theme.TABLE_HEADER_BG);
        table.setSelectionBackground(Theme.ACCENT);
        table.setSelectionForeground(Color.WHITE);
        return table;
    }

    public static JPanel getDispatchPanel(){
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 8));
        Utility obj = new Utility();
        JComboBox<String> cb1 = new JComboBox<String>(obj.getProductList());
        JComboBox<String> cb2 = new JComboBox<String>(obj.getColourList());
        JComboBox<String> cb3 = new JComboBox<String>(obj.getWeightList());

        JTextField tf1 = new JTextField(5);

        panel.add(Theme.labeledField("Produto", cb1));
        panel.add(Theme.labeledField("Cor", cb2));
        panel.add(Theme.labeledField("Peso", cb3));
        panel.add(Theme.labeledField("Quantidade", tf1));

        JButton rmvBtn = new JButton("Despachar");
        rmvBtn.setFont(Theme.FONT_BUTTON);
        rmvBtn.setBackground(Theme.ACCENT);
        rmvBtn.setForeground(Color.WHITE);
        panel.add(rmvBtn);

        rmvBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                String product = cb1.getSelectedItem().toString();
                String colour = cb2.getSelectedItem().toString();
                String weight = cb3.getSelectedItem().toString();
                int qty;
                try {
                    qty = Integer.parseInt(tf1.getText());
                } catch (NumberFormatException nfe) {
                    JOptionPane.showMessageDialog(IMStart.frame, "Informe uma quantidade válida.", "AVISO", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                rmvBtn.setEnabled(false);
                panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                new SwingWorker<java.util.List<Object[]>, Void>() {
                    @Override
                    protected java.util.List<Object[]> doInBackground() throws Exception {
                        ResultSet dataNew = Db.sellProduct(product, colour, weight, qty);
                        java.util.List<Object[]> rows = new java.util.ArrayList<>();
                        while (dataNew.next()) {
                            rows.add(new Object[]{dataNew.getString(1), dataNew.getString(2), dataNew.getString(3), dataNew.getString(4), dataNew.getString(5)});
                        }
                        return rows;
                    }

                    @Override
                    protected void done() {
                        panel.setCursor(Cursor.getDefaultCursor());
                        rmvBtn.setEnabled(true);
                        try {
                            model.setRowCount(0);
                            for (Object[] row : get()) {
                                model.addRow(row);
                            }
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        } catch (java.util.concurrent.ExecutionException e) {
                            JOptionPane.showMessageDialog(IMStart.frame, "Não foi possível despachar o produto.", "ERRO", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }.execute();
            }
        });
        panel.setBackground(Theme.SURFACE);
        panel.setBorder(Theme.sectionBorder("Despachar estoque"));
        return panel;
    }
}
