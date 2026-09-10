package br.com.lojagenerica.pdvclient.ui;

import br.com.lojagenerica.pdvclient.local.FormaPagamentoCache;
import br.com.lojagenerica.pdvclient.local.FormaPagamentoCacheDao;
import br.com.lojagenerica.pdvclient.local.ItemVendaLocal;
import br.com.lojagenerica.pdvclient.local.LocalEstoqueCache;
import br.com.lojagenerica.pdvclient.local.LocalEstoqueCacheDao;
import br.com.lojagenerica.pdvclient.local.PagamentoVendaLocal;
import br.com.lojagenerica.pdvclient.local.ProdutoCache;
import br.com.lojagenerica.pdvclient.local.ProdutoCacheDao;
import br.com.lojagenerica.pdvclient.local.VendaLocalDao;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/**
 * Tela de venda nova, contra o backbone offline (cache local + outbox) em
 * vez do SQLite direto do legado ({@code mysquare.core.Sale}). Sem
 * cascata cor/peso (específica do IMS antigo) — busca por nome ou código
 * de barras contra {@link ProdutoCacheDao}, pagamento único por venda (o
 * modelo aceita vários, mas a UI para dividir fica pra quando alguém
 * pedir), sem desconto ainda (precisa de tela de login pra identificar
 * quem está vendendo — ver {@code VendaService.validarDesconto} no
 * servidor) e sem impressão de recibo ainda (fica pra próxima leva).
 *
 * <p>Não verificado visualmente nesta sessão (sem display disponível).
 */
public final class VendaScreen extends JPanel {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private record ItemCarrinho(ProdutoCache produto, double quantidade, double precoUnitario) {
        double subtotal() {
            return quantidade * precoUnitario;
        }
    }

    private final ProdutoCacheDao produtoCacheDao;
    private final FormaPagamentoCacheDao formaPagamentoCacheDao;
    private final LocalEstoqueCacheDao localEstoqueCacheDao;
    private final VendaLocalDao vendaLocalDao;

    private final List<ItemCarrinho> carrinho = new ArrayList<>();
    private final DefaultTableModel modeloCarrinho = new DefaultTableModel(
            new Object[]{"Produto", "Quantidade", "Preço unit.", "Subtotal"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable tabelaCarrinho = new JTable(modeloCarrinho);
    private final JLabel totalLabel = new JLabel("Total: R$ 0,00");

    private final JTextField campoBusca = new JTextField(20);
    private final JTextField campoCodigoBarras = new JTextField(14);
    private final JTextField campoQuantidade = new JTextField("1", 5);
    private final DefaultListModel<ProdutoCache> modeloResultados = new DefaultListModel<>();
    private final JList<ProdutoCache> listaResultados = new JList<>(modeloResultados);
    private final javax.swing.JComboBox<LocalEstoqueCache> comboLocalEstoque = new javax.swing.JComboBox<>();

    public VendaScreen(ProdutoCacheDao produtoCacheDao, FormaPagamentoCacheDao formaPagamentoCacheDao,
                        LocalEstoqueCacheDao localEstoqueCacheDao, VendaLocalDao vendaLocalDao) {
        super(new BorderLayout());
        this.produtoCacheDao = produtoCacheDao;
        this.formaPagamentoCacheDao = formaPagamentoCacheDao;
        this.localEstoqueCacheDao = localEstoqueCacheDao;
        this.vendaLocalDao = vendaLocalDao;
        setBackground(Theme.BACKGROUND);

        montarLayout();
        carregarLocaisEstoque();
    }

    private void montarLayout() {
        tabelaCarrinho.setRowHeight(30);
        tabelaCarrinho.setFont(Theme.FONT_FIELD);
        tabelaCarrinho.setSelectionBackground(Theme.ACCENT);
        tabelaCarrinho.setSelectionForeground(Color.WHITE);

        listaResultados.setVisibleRowCount(4);
        listaResultados.setCellRenderer((list, produto, index, selected, focus) ->
                new JLabel(produto.nome() + (produto.precoVenda() == null ? "  (sem preço)"
                        : String.format(PT_BR, "  —  R$ %.2f", produto.precoVenda()))));

        campoBusca.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                buscar();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                buscar();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                buscar();
            }
        });

        campoCodigoBarras.addActionListener(e -> adicionarPorCodigoDeBarras());

        JButton botaoAdicionar = new JButton("Adicionar à venda");
        botaoAdicionar.setFont(Theme.FONT_BUTTON);
        botaoAdicionar.addActionListener(e -> adicionarSelecionadoAoCarrinho());

        JButton botaoRemover = new JButton("Remover linha selecionada");
        botaoRemover.addActionListener(e -> removerLinhaSelecionada());

        JButton botaoConfirmar = new JButton("Confirmar venda");
        botaoConfirmar.setFont(Theme.FONT_BUTTON);
        botaoConfirmar.setBackground(Theme.ACCENT);
        botaoConfirmar.setForeground(Color.WHITE);
        botaoConfirmar.addActionListener(e -> confirmarVenda(botaoConfirmar));

        JButton botaoCancelar = new JButton("Cancelar venda");
        botaoCancelar.addActionListener(e -> limparCarrinho());

        totalLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        totalLabel.setForeground(Theme.ACCENT_DARK);

        JPanel linhaBusca = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 6));
        linhaBusca.setOpaque(false);
        linhaBusca.add(Theme.labeledField("Código de barras", campoCodigoBarras));
        linhaBusca.add(Theme.labeledField("Buscar produto (nome)", campoBusca));
        linhaBusca.add(Theme.labeledField("Quantidade", campoQuantidade));
        linhaBusca.add(botaoAdicionar);

        JPanel painelResultados = new JPanel(new BorderLayout());
        painelResultados.setOpaque(false);
        painelResultados.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));
        painelResultados.add(new JScrollPane(listaResultados), BorderLayout.CENTER);

        JPanel linhaLocalEstoque = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 6));
        linhaLocalEstoque.setOpaque(false);
        linhaLocalEstoque.add(Theme.labeledField("Local de estoque", comboLocalEstoque));

        JPanel linhaAcoes = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 8));
        linhaAcoes.setOpaque(false);
        linhaAcoes.add(botaoRemover);
        linhaAcoes.add(totalLabel);
        linhaAcoes.add(botaoConfirmar);
        linhaAcoes.add(botaoCancelar);

        JPanel rodape = new JPanel();
        rodape.setLayout(new BoxLayout(rodape, BoxLayout.Y_AXIS));
        rodape.setBackground(Theme.SURFACE);
        rodape.setBorder(Theme.sectionBorder("Nova venda"));
        rodape.add(linhaLocalEstoque);
        rodape.add(linhaBusca);
        rodape.add(painelResultados);
        rodape.add(linhaAcoes);

        add(new JScrollPane(tabelaCarrinho), BorderLayout.CENTER);
        add(rodape, BorderLayout.SOUTH);
    }

    private void carregarLocaisEstoque() {
        try {
            List<LocalEstoqueCache> locais = localEstoqueCacheDao.listarAtivos();
            comboLocalEstoque.removeAllItems();
            LocalEstoqueCache selecionarPorPadrao = null;
            for (LocalEstoqueCache local : locais) {
                comboLocalEstoque.addItem(local);
                if (local.principal()) {
                    selecionarPorPadrao = local;
                }
            }
            if (selecionarPorPadrao != null) {
                comboLocalEstoque.setSelectedItem(selecionarPorPadrao);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Não foi possível carregar os locais de estoque.\n" + e.getMessage(),
                    "ERRO", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void buscar() {
        String termo = campoBusca.getText().trim();
        modeloResultados.clear();
        if (termo.isEmpty()) {
            return;
        }
        try {
            for (ProdutoCache produto : produtoCacheDao.buscarPorNomeOuCodigo(termo)) {
                modeloResultados.addElement(produto);
            }
        } catch (Exception e) {
            // busca local falhou (banco corrompido?) — silencioso pra não travar a digitação; próxima tecla tenta de novo.
        }
    }

    private void adicionarPorCodigoDeBarras() {
        String codigo = campoCodigoBarras.getText().trim();
        campoCodigoBarras.setText("");
        if (codigo.isEmpty()) {
            return;
        }
        try {
            ProdutoCache produto = produtoCacheDao.buscarPorCodigoExato(codigo);
            if (produto == null) {
                JOptionPane.showMessageDialog(this, "Nenhum produto cadastrado com este código.",
                        "Código não encontrado", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Um código bipado é sempre "1 unidade, agora" — sem clique extra.
            adicionarAoCarrinho(produto, 1.0);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Erro ao buscar o produto.\n" + e.getMessage(), "ERRO", JOptionPane.ERROR_MESSAGE);
        } finally {
            campoCodigoBarras.requestFocusInWindow();
        }
    }

    private void adicionarSelecionadoAoCarrinho() {
        ProdutoCache selecionado = listaResultados.getSelectedValue();
        if (selecionado == null) {
            JOptionPane.showMessageDialog(this, "Busque e selecione um produto na lista primeiro.",
                    "AVISO", JOptionPane.WARNING_MESSAGE);
            return;
        }
        double quantidade;
        try {
            quantidade = Double.parseDouble(campoQuantidade.getText().trim().replace(",", "."));
            if (quantidade <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this, "Informe uma quantidade válida.", "AVISO", JOptionPane.WARNING_MESSAGE);
            return;
        }
        adicionarAoCarrinho(selecionado, quantidade);
    }

    private void adicionarAoCarrinho(ProdutoCache produto, double quantidade) {
        if (produto.precoVenda() == null) {
            JOptionPane.showMessageDialog(this,
                    "\"" + produto.nome() + "\" não tem preço de venda cadastrado. Cadastre o preço no painel web primeiro.",
                    "Sem preço", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ItemCarrinho item = new ItemCarrinho(produto, quantidade, produto.precoVenda());
        carrinho.add(item);
        modeloCarrinho.addRow(new Object[]{produto.nome(), formatarQuantidade(quantidade),
                String.format(PT_BR, "%.2f", item.precoUnitario()), String.format(PT_BR, "%.2f", item.subtotal())});
        recalcularTotal();
        campoQuantidade.setText("1");
        campoBusca.setText("");
        modeloResultados.clear();
    }

    private void removerLinhaSelecionada() {
        int linha = tabelaCarrinho.getSelectedRow();
        if (linha < 0) {
            return;
        }
        carrinho.remove(linha);
        modeloCarrinho.removeRow(linha);
        recalcularTotal();
    }

    private void limparCarrinho() {
        carrinho.clear();
        modeloCarrinho.setRowCount(0);
        recalcularTotal();
    }

    private void recalcularTotal() {
        double total = carrinho.stream().mapToDouble(ItemCarrinho::subtotal).sum();
        totalLabel.setText(String.format(PT_BR, "Total: R$ %.2f", total));
    }

    private void confirmarVenda(JButton botaoConfirmar) {
        if (carrinho.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Adicione pelo menos um item primeiro.", "AVISO", JOptionPane.WARNING_MESSAGE);
            return;
        }
        LocalEstoqueCache localEstoque = (LocalEstoqueCache) comboLocalEstoque.getSelectedItem();
        if (localEstoque == null) {
            JOptionPane.showMessageDialog(this, "Nenhum local de estoque configurado — sincronize com o servidor primeiro.",
                    "AVISO", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<FormaPagamentoCache> formas;
        try {
            formas = formaPagamentoCacheDao.listarAtivas();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Erro ao carregar formas de pagamento.\n" + e.getMessage(), "ERRO", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (formas.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nenhuma forma de pagamento configurada — cadastre pelo menos uma no painel web.",
                    "AVISO", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double total = carrinho.stream().mapToDouble(ItemCarrinho::subtotal).sum();
        FormaPagamentoCache formaEscolhida = (FormaPagamentoCache) JOptionPane.showInputDialog(this,
                "Forma de pagamento para o total de " + String.format(PT_BR, "%.2f", total) + ":",
                "Confirmar venda", JOptionPane.QUESTION_MESSAGE, null, formas.toArray(), formas.get(0));
        if (formaEscolhida == null) {
            return;
        }

        double valorRecebido = total;
        double troco = 0;
        if ("DINHEIRO".equals(formaEscolhida.natureza())) {
            Double informado = perguntarValorRecebido(total);
            if (informado == null) {
                return;
            }
            valorRecebido = informado;
            troco = valorRecebido - total;
        }

        List<ItemVendaLocal> itens = carrinho.stream()
                .map(i -> new ItemVendaLocal(i.produto().id(), i.produto().nome(), i.quantidade(),
                        i.produto().unidadeEstoqueId(), i.precoUnitario(), null))
                .toList();
        List<PagamentoVendaLocal> pagamentos = List.of(
                new PagamentoVendaLocal(formaEscolhida.id(), total, valorRecebido, troco));

        double trocoFinal = troco;
        botaoConfirmar.setEnabled(false);
        new SwingWorker<UUID, Void>() {
            @Override
            protected UUID doInBackground() throws Exception {
                // usuarioId null: ainda não existe tela de login no PDV — quando existir, passa o
                // usuário autenticado aqui (ver docs/ROADMAP.md, Fase D).
                return vendaLocalDao.registrarVenda(localEstoque.id(), null, null, null, itens, pagamentos);
            }

            @Override
            protected void done() {
                botaoConfirmar.setEnabled(true);
                try {
                    get();
                    limparCarrinho();
                    String mensagem = "Venda registrada. Será sincronizada com o servidor em instantes.";
                    if (trocoFinal > 0) {
                        mensagem += "\nTroco: " + String.format(PT_BR, "%.2f", trocoFinal);
                    }
                    JOptionPane.showMessageDialog(VendaScreen.this, mensagem);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(VendaScreen.this,
                            "Não foi possível registrar a venda localmente.\n" + e.getMessage(), "ERRO", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private Double perguntarValorRecebido(double total) {
        while (true) {
            String input = JOptionPane.showInputDialog(this,
                    "Valor recebido do cliente (total: " + String.format(PT_BR, "%.2f", total) + "):",
                    "Pagamento em dinheiro", JOptionPane.QUESTION_MESSAGE);
            if (input == null) {
                return null;
            }
            double valor;
            try {
                valor = Double.parseDouble(input.trim().replace(",", "."));
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(this, "Valor inválido.", "AVISO", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (valor < total) {
                JOptionPane.showMessageDialog(this,
                        "Valor insuficiente. Faltam " + String.format(PT_BR, "%.2f", total - valor) + ".",
                        "AVISO", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            return valor;
        }
    }

    private String formatarQuantidade(double quantidade) {
        return quantidade == Math.floor(quantidade) ? String.valueOf((long) quantidade) : String.valueOf(quantidade);
    }
}
