package mysquare.core;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.ResultSet;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

/** Month-grid view of gross revenue per day; clicking a day lists the sales made on it. */
public class SalesCalendar {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final String[] DIAS_SEMANA = {"Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"};
    private static final String[] MESES = {
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    };

    public static JPanel getCalendarPanel() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BACKGROUND);

        Calendar today = Calendar.getInstance();
        // {year, month (1-12)}; captured by the header/grid rebuild below, mutated by ◀ / ▶.
        final int[] displayed = {today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1};

        JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
        monthLabel.setFont(Theme.FONT_TITLE);
        monthLabel.setForeground(Theme.ACCENT_DARK);

        JButton prevBtn = new JButton("◀");
        JButton nextBtn = new JButton("▶");
        prevBtn.setFont(Theme.FONT_BUTTON);
        nextBtn.setFont(Theme.FONT_BUTTON);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        header.add(prevBtn, BorderLayout.WEST);
        header.add(monthLabel, BorderLayout.CENTER);
        header.add(nextBtn, BorderLayout.EAST);

        JPanel gridHolder = new JPanel(new BorderLayout());
        gridHolder.setOpaque(false);

        // Self-referencing lambda: ◀/▶ need to call the same "rebuild the grid" logic again,
        // which a plain local variable can't do before its own initializer finishes.
        final Runnable[] renderMonth = new Runnable[1];
        renderMonth[0] = () -> {
            int year = displayed[0];
            int month = displayed[1];
            monthLabel.setText(MESES[month - 1] + " " + year);
            prevBtn.setEnabled(false);
            nextBtn.setEnabled(false);
            root.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            new SwingWorker<Map<Integer, double[]>, Void>() {
                @Override
                protected Map<Integer, double[]> doInBackground() throws Exception {
                    return Db.fetchMonthSummary(year, month);
                }

                @Override
                protected void done() {
                    root.setCursor(Cursor.getDefaultCursor());
                    prevBtn.setEnabled(true);
                    nextBtn.setEnabled(true);
                    try {
                        Map<Integer, double[]> summary = get();
                        gridHolder.removeAll();
                        gridHolder.add(buildGrid(year, month, summary), BorderLayout.CENTER);
                        gridHolder.revalidate();
                        gridHolder.repaint();
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(IMStart.frame,
                                "Não foi possível carregar o calendário.\nERRO:" + ex.getMessage(), "ERRO", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        };

        prevBtn.addActionListener(e -> {
            displayed[1]--;
            if (displayed[1] < 1) {
                displayed[1] = 12;
                displayed[0]--;
            }
            renderMonth[0].run();
        });
        nextBtn.addActionListener(e -> {
            displayed[1]++;
            if (displayed[1] > 12) {
                displayed[1] = 1;
                displayed[0]++;
            }
            renderMonth[0].run();
        });

        renderMonth[0].run();

        root.add(header, BorderLayout.NORTH);
        root.add(gridHolder, BorderLayout.CENTER);
        return root;
    }

    private static JPanel buildGrid(int year, int month, Map<Integer, double[]> summary) {
        JPanel grid = new JPanel(new GridLayout(0, 7, 4, 4));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(4, 10, 10, 10));

        for (String nomeDia : DIAS_SEMANA) {
            JLabel lbl = new JLabel(nomeDia, SwingConstants.CENTER);
            lbl.setFont(Theme.FONT_TABLE_HEADER);
            lbl.setForeground(Theme.TEXT_MUTED);
            grid.add(lbl);
        }

        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 1);
        int firstWeekday = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0 = Sunday, matching DIAS_SEMANA
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = 0; i < firstWeekday; i++) {
            grid.add(emptyCell());
        }
        for (int day = 1; day <= daysInMonth; day++) {
            double[] dado = summary.get(day);
            grid.add(dayCell(year, month, day, dado));
        }

        return grid;
    }

    private static JComponent emptyCell() {
        JLabel blank = new JLabel("");
        blank.setOpaque(false);
        return blank;
    }

    private static JComponent dayCell(int year, int month, int day, double[] dado) {
        boolean temVenda = dado != null && dado[2] > 0;

        JButton cell = new JButton();
        cell.setLayout(new BorderLayout());
        cell.setFocusPainted(false);
        cell.setMargin(new Insets(4, 4, 4, 4));
        cell.setHorizontalAlignment(SwingConstants.LEFT);
        cell.setVerticalAlignment(SwingConstants.TOP);

        JLabel dayLabel = new JLabel(String.valueOf(day));
        dayLabel.setFont(Theme.FONT_TABLE_HEADER);

        if (temVenda) {
            cell.setBackground(Theme.TABLE_HEADER_BG);
            dayLabel.setForeground(Theme.ACCENT_DARK);
            JLabel valorLabel = new JLabel(String.format(PT_BR, "R$ %.2f", dado[2]));
            valorLabel.setFont(Theme.FONT_LABEL.deriveFont(Font.BOLD, 14f));
            valorLabel.setForeground(Theme.ACCENT);
            JPanel content = new JPanel(new BorderLayout());
            content.setOpaque(false);
            content.add(dayLabel, BorderLayout.NORTH);
            content.add(valorLabel, BorderLayout.SOUTH);
            cell.add(content, BorderLayout.CENTER);
            String diaBr = String.format("%02d/%02d/%04d", day, month, year);
            cell.addActionListener(e -> mostrarVendasDoDia(diaBr));
        } else {
            cell.setBackground(Theme.SURFACE);
            dayLabel.setForeground(Theme.TEXT_MUTED);
            cell.add(dayLabel, BorderLayout.NORTH);
            cell.setEnabled(false);
        }

        cell.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        cell.setPreferredSize(new Dimension(140, 70));
        return cell;
    }

    private static void mostrarVendasDoDia(String diaBr) {
        DefaultTableModel model = new DefaultTableModel();
        JTable table = new JTable(model);
        model.addColumn("HORA");
        model.addColumn("PRODUTO");
        model.addColumn("COR");
        model.addColumn("PESO");
        model.addColumn("QTD");
        model.addColumn("PREÇO UNIT.");
        model.addColumn("SUBTOTAL");

        double total = 0;
        try {
            ResultSet rs = Db.fetchSalesForDay(diaBr);
            while (rs.next()) {
                double preco = rs.getDouble("pprice");
                int qtd = rs.getInt("quantity");
                double subtotal = qtd * preco;
                total += subtotal;
                String horario = rs.getString("timestamp");
                model.addRow(new Object[]{
                        horario.length() >= 19 ? horario.substring(11) : horario,
                        rs.getString("product"),
                        rs.getString("colour"),
                        rs.getString("weight"),
                        qtd,
                        String.format(PT_BR, "%.2f", preco),
                        String.format(PT_BR, "%.2f", subtotal)
                });
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(IMStart.frame,
                    "Não foi possível carregar as vendas do dia.\nERRO:" + ex.getMessage(), "ERRO", JOptionPane.ERROR_MESSAGE);
            return;
        }

        table.setGridColor(Theme.BORDER);
        table.setRowHeight(Theme.TABLE_ROW_HEIGHT);
        table.setFont(Theme.FONT_TABLE);
        table.getTableHeader().setFont(Theme.FONT_TABLE_HEADER);
        table.getTableHeader().setBackground(Theme.TABLE_HEADER_BG);
        table.setSelectionBackground(Theme.ACCENT);
        table.setSelectionForeground(Color.WHITE);

        JLabel totalLabel = new JLabel(String.format(PT_BR, "Total do dia: R$ %.2f", total));
        totalLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        totalLabel.setForeground(Theme.ACCENT_DARK);
        totalLabel.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(totalLabel, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(600, 300));

        JOptionPane.showMessageDialog(IMStart.frame, panel, "Vendas em " + diaBr, JOptionPane.PLAIN_MESSAGE);
    }
}
