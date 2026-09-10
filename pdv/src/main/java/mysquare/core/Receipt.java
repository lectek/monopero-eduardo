package mysquare.core;

import javax.swing.JOptionPane;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Prints a simple 80mm-thermal-style receipt for a confirmed sale, using whatever printer Windows has configured. */
final class Receipt {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final String STORE_NAME = "Mini Mercadinho Rota o Melhor da Zona Sul";

    private static final double MM_TO_PT = 72.0 / 25.4;
    private static final double PAPER_WIDTH_MM = 80.0;
    private static final double MARGIN_MM = 3.0;
    private static final double PAPER_HEIGHT_MM = 500.0;

    private Receipt() {
    }

    /** Sends a receipt for these lines/total to the default printer; shows a dialog instead if none is configured. */
    static void print(List<Line> lines, double total) {
        print(lines, total, null);
    }

    /**
     * Same as {@link #print(List, double)}, plus a payment-method line (e.g. "Pagamento: Dinheiro | Recebido: 50,00 | Troco: 10,00")
     * printed right after the total; pass null to omit it.
     */
    static void print(List<Line> lines, double total, String paymentSummary) {
        PrinterJob job = PrinterJob.getPrinterJob();
        if (job.getPrintService() == null) {
            JOptionPane.showMessageDialog(IMStart.frame,
                    "Nenhuma impressora está configurada no Windows.\nA venda foi confirmada normalmente, mas a nota não pôde ser impressa.",
                    "Impressão indisponível", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double widthPt = PAPER_WIDTH_MM * MM_TO_PT;
        double heightPt = PAPER_HEIGHT_MM * MM_TO_PT;
        double marginPt = MARGIN_MM * MM_TO_PT;
        Paper paper = new Paper();
        paper.setSize(widthPt, heightPt);
        paper.setImageableArea(marginPt, marginPt, widthPt - 2 * marginPt, heightPt - 2 * marginPt);
        PageFormat format = new PageFormat();
        format.setPaper(paper);

        List<String> receiptLines = buildLines(lines, total, paymentSummary);
        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            if (pageIndex > 0) {
                return Printable.NO_SUCH_PAGE;
            }
            Graphics2D g2 = (Graphics2D) graphics;
            g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            g2.setFont(new Font("Monospaced", Font.PLAIN, 8));
            FontMetrics metrics = g2.getFontMetrics();
            int width = (int) pageFormat.getImageableWidth();
            int y = metrics.getAscent();
            for (String line : receiptLines) {
                for (String wrapped : wrap(line, metrics, width)) {
                    g2.drawString(wrapped, 0, y);
                    y += metrics.getHeight();
                }
            }
            return Printable.PAGE_EXISTS;
        }, format);

        try {
            job.print();
        } catch (PrinterException e) {
            JOptionPane.showMessageDialog(IMStart.frame,
                    "A venda foi confirmada normalmente, mas não foi possível imprimir a nota.\n" + e.getMessage(),
                    "Erro de impressão", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static List<String> wrap(String text, FontMetrics metrics, int maxWidth) {
        List<String> result = new ArrayList<String>();
        if (metrics.stringWidth(text) <= maxWidth) {
            result.add(text);
            return result;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (metrics.stringWidth(candidate) > maxWidth && current.length() > 0) {
                result.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private static List<String> buildLines(List<Line> lines, double total, String paymentSummary) {
        List<String> out = new ArrayList<String>();
        out.add(STORE_NAME);
        out.add(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", PT_BR).format(new Date()));
        out.add("--------------------------------");
        for (Line line : lines) {
            out.add(line.product + " - " + line.colour + " - " + line.weight);
            out.add(String.format(PT_BR, "  %d x %.2f = %.2f", line.qty, line.unitPrice, line.subtotal()));
        }
        out.add("--------------------------------");
        out.add(String.format(PT_BR, "TOTAL: %.2f", total));
        if (paymentSummary != null && !paymentSummary.isEmpty()) {
            out.add(paymentSummary);
        }
        out.add("");
        out.add("Obrigado pela preferência!");
        return out;
    }

    static class Line {
        final String product;
        final String colour;
        final String weight;
        final int qty;
        final double unitPrice;

        Line(String product, String colour, String weight, int qty, double unitPrice) {
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
}
