package mysquare.core;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.ResultSet;
import java.util.Locale;

/** Read-only summary of every sale/dispatch, grouped by calendar day. */
public class SalesReport {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    public static JTable getSalesReportView() throws Exception {
        DefaultTableModel model = new DefaultTableModel();
        JTable table = new JTable(model);
        model.addColumn("DATA");
        model.addColumn("VENDAS");
        model.addColumn("ITENS");
        model.addColumn("TOTAL (R$)");

        try {
            ResultSet data = Db.fetchSalesByDate();
            while (data.next()) {
                model.addRow(new Object[]{
                        data.getString("dia"),
                        data.getInt("vendas"),
                        data.getInt("itens"),
                        String.format(PT_BR, "%.2f", data.getDouble("total"))
                });
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
}
