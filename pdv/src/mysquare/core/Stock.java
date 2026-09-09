package mysquare.core;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.ResultSet;

public class Stock {

    public static JTable getStockView() throws Exception{
        DefaultTableModel model = new DefaultTableModel();
        JTable table = new JTable(model);
        model.addColumn("PRODUTO");
        model.addColumn("COR");
        model.addColumn("PESO");
        model.addColumn("QUANTIDADE");
        model.addColumn("CÓDIGO");
        model.addColumn("DESCRIÇÃO");
        model.addColumn("PREÇO");
        ResultSet data = null;
        try {
            data = Db.fetchProducts();
            while(data.next()) {
                model.addRow(new Object[]{data.getString("pname"), data.getString("pclr"), data.getString("pwt"),
                        data.getString("pqt"), data.getString("pcode"), data.getString("pdesc"), data.getString("pprice")});
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


