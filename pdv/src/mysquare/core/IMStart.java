package mysquare.core;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.concurrent.Callable;
import javax.swing.*;

public class IMStart {

	public static JFrame frame = new JFrame();
	public static JMenuBar mb = new JMenuBar();
	public static JMenu m0,m1,m2,m3,m4,m5;
	public static JMenuItem m0i1, m1i1, m2i1, m2i2, m2i3, m2i4, m2i5, m2i6, m3i1, m3i2, m4i1, m5i1;
	public static JScrollPane jScrollPane;

	private static final String AJUDA_HOME = "Tela inicial: use os botões para acessar rapidamente cada parte do sistema. "
			+ "No topo, veja o faturamento bruto de hoje, do mês, do ano ou desde o início do uso do software — "
			+ "escolha o período clicando nos botões acima do valor e depois em \"Atualizar\" para trazer os números mais recentes.";
	private static final String AJUDA_VENDA = "Escolha o produto, a cor, o peso e a quantidade e clique em \"Adicionar à venda\". "
			+ "Repita para cada item que o cliente está levando — o total é somado automaticamente. "
			+ "Quando terminar, clique em \"Confirmar venda\" para dar baixa no estoque, "
			+ "ou \"Cancelar venda\" para descartar sem alterar o estoque.";
	private static final String AJUDA_PRODUCAO = "Registre aqui um novo lote de produção: informe produto, cor e peso "
			+ "(pode digitar um valor novo, não precisa já existir), a quantidade fabricada e, se quiser, "
			+ "código, descrição e preço. Clique em \"Adicionar produto\" para registrar. "
			+ "A tabela acima mostra o histórico de tudo que já foi produzido.";
	private static final String AJUDA_DESPACHO = "Use esta tela para dar baixa manual no estoque (por exemplo, perda, "
			+ "doação ou transferência) sem passar pela tela de Venda. Escolha o produto existente, a quantidade "
			+ "e clique em \"Despachar\".";
	private static final String AJUDA_ESTOQUE = "Esta tela mostra a quantidade atual de cada produto em estoque. "
			+ "Para alterar o estoque, use as telas Venda, Produção (entrada) ou Despacho (saída), "
			+ "ou edite diretamente em Modificar produtos.";
	private static final String AJUDA_VENDAS_POR_DIA = "Resumo de tudo que foi vendido ou despachado, agrupado por dia: "
			+ "número de vendas, total de itens e valor total. O valor só é somado para vendas feitas pela tela "
			+ "Venda (que registra o preço); despachos manuais sem preço entram na contagem de itens mas não no total em R$.";
	private static final String AJUDA_CALENDARIO = "Veja o faturamento bruto de cada dia em um calendário mensal. "
			+ "Use as setas ◀ / ▶ para trocar de mês. Dias com venda mostram o valor total faturado; "
			+ "clique em um desses dias para ver a lista de vendas feitas nele. "
			+ "O valor é somado apenas para vendas com preço registrado, assim como em \"Vendas por dia\".";
	private static final String AJUDA_MODIFICAR = "Selecione um produto existente na lista para carregar seus dados. "
			+ "Altere o que precisar (nome, cor, peso, quantidade, código, preço ou descrição) e clique em \"Salvar alterações\". "
			+ "Para remover um produto do catálogo, selecione-o e clique em \"Excluir produto\" — "
			+ "isso não apaga o histórico de produção/despacho dele.";
	private static final String AJUDA_CONFIGURACOES = "Cole aqui o Access Token de produção do Mercado Pago "
			+ "(painel de desenvolvedores → Credenciais de produção) para ativar o pagamento via Pix na tela de Venda. "
			+ "Deixe em branco para manter o Pix desativado. O e-mail padrão do pagador é usado apenas porque a API "
			+ "do Mercado Pago exige um e-mail — pode ser qualquer e-mail da loja, já que a venda é presencial. "
			+ "Mais abaixo, cole o endereço do site (loja online) para o botão \"Loja Online\" da tela inicial funcionar.";
	private static final String AJUDA_ADMIN_SAAS = "Crie ou atualize aqui os acessos ao site (SaaS). "
			+ "Este é o único lugar onde esses acessos são criados — o site não permite se auto-cadastrar. "
			+ "Escolha o tipo: \"Admin\" pra mexer no painel administrativo, ou \"Motoboy\" pra entrar na "
			+ "área de entregas pelo celular (nesse caso, defina também o % de comissão dele sobre o frete "
			+ "de cada entrega). Preencha os dados e clique em \"Salvar acesso\"; se o e-mail já existir, "
			+ "os dados (inclusive o tipo) são atualizados.";

	IMStart(){
		m0 = new JMenu("Início");
		m0.setMnemonic(KeyEvent.VK_I);
		m1 = new JMenu("Venda");
		m1.setMnemonic(KeyEvent.VK_V);
		m2 = new JMenu("Exibir");
		m2.setMnemonic(KeyEvent.VK_E);
		m3 = new JMenu("Operações");
		m3.setMnemonic(KeyEvent.VK_O);
		m4 = new JMenu("Ajuda");
		m4.setMnemonic(KeyEvent.VK_A);
		m5 = new JMenu("Configurações");
		m5.setMnemonic(KeyEvent.VK_C);
		m0i1 = new JMenuItem("Página inicial");
		m1i1 = new JMenuItem("Nova venda");
		m2i1 = new JMenuItem("Produção");
		m2i2 = new JMenuItem("Despacho");
		m2i3 = new JMenuItem("Estoque");
		m2i4 = new JMenuItem("Vendas por dia");
		m2i5 = new JMenuItem("Calendário");
		m2i6 = new JMenuItem("WhatsApp Web");
		m3i1 = new JMenuItem("Modificar produtos");
		m3i2 = new JMenuItem("Acessos do site");
		m4i1 = new JMenuItem("Sobre o software");
		m5i1 = new JMenuItem("Pagamento (Pix)");
		m0.add(m0i1);
		m1.add(m1i1);
		m2.add(m2i1);
		m2.add(m2i2);
		m2.add(m2i3);
		m2.add(m2i4);
		m2.add(m2i5);
		m2.add(m2i6);
		m3.add(m3i1);
		m3.add(m3i2);
		m4.add(m4i1);
		m5.add(m5i1);
		mb.add(m0);
		mb.add(m1);
		mb.add(m2);
		mb.add(m3);
		mb.add(m4);
		mb.add(m5);
		m0i1.setBackground(Theme.TABLE_HEADER_BG);
		m1i1.setBackground(Theme.TABLE_HEADER_BG);
		m2i1.setBackground(Theme.TABLE_HEADER_BG);
        m2i2.setBackground(Theme.TABLE_HEADER_BG);
        m2i3.setBackground(Theme.TABLE_HEADER_BG);
        m2i4.setBackground(Theme.TABLE_HEADER_BG);
        m2i5.setBackground(Theme.TABLE_HEADER_BG);
        m2i6.setBackground(Theme.TABLE_HEADER_BG);
        m3i1.setBackground(Theme.TABLE_HEADER_BG);
        m3i2.setBackground(Theme.TABLE_HEADER_BG);
        m4i1.setBackground(Theme.TABLE_HEADER_BG);
        m5i1.setBackground(Theme.TABLE_HEADER_BG);
        mb.setBackground(Theme.SURFACE);
	}

	/** Nests an existing screen's content above the shared "Como usar" / Instagram footer. */
	private static JPanel withFooter(Component content, String helpTitle, String helpBody) {
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.add(content, BorderLayout.CENTER);
		wrap.add(Theme.footer(helpTitle, helpBody), BorderLayout.SOUTH);
		return wrap;
	}

	/** Swaps the frame's content for one screen: build the centre + footer, then lay out and repaint. */
	private static void showScreen(Callable<Component> center, Callable<Component> south, String errorMsg) {
		try {
			frame.getContentPane().removeAll();
			frame.getContentPane().add(BorderLayout.CENTER, center.call());
			frame.getContentPane().add(BorderLayout.SOUTH, south.call());
			frame.getContentPane().doLayout();
			frame.update(frame.getGraphics());
			frame.setVisible(true);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(frame, errorMsg + "\nERRO:" + ex.getMessage(), "ERRO", JOptionPane.ERROR_MESSAGE);
		}
	}

	public static void showHome() {
		showScreen(Home::getHomePanel, () -> Theme.footer("Início", AJUDA_HOME), "Não foi possível abrir a tela inicial.");
	}

	public static void showVenda() {
		showScreen(Sale::getSalePanel, () -> Theme.footer("Venda", AJUDA_VENDA), "Não foi possível abrir a tela de venda.");
	}

	public static void showProducao() {
		showScreen(() -> new JScrollPane(Production.getProductionView()),
				() -> withFooter(Production.getProductionPanel(), "Produção", AJUDA_PRODUCAO),
				"Não foi possível obter os produtos fabricados.");
	}

	public static void showDespacho() {
		showScreen(() -> new JScrollPane(Dispatch.getDispatchView()),
				() -> withFooter(Dispatch.getDispatchPanel(), "Despacho", AJUDA_DESPACHO),
				"Não foi possível obter os produtos despachados.");
	}

	public static void showEstoque() {
		showScreen(() -> new JScrollPane(Stock.getStockView()), () -> Theme.footer("Estoque", AJUDA_ESTOQUE),
				"Não foi possível obter o estoque.");
	}

	public static void showVendasPorDia() {
		showScreen(() -> new JScrollPane(SalesReport.getSalesReportView()), () -> Theme.footer("Vendas por dia", AJUDA_VENDAS_POR_DIA),
				"Não foi possível obter o relatório de vendas.");
	}

	public static void showCalendario() {
		showScreen(SalesCalendar::getCalendarPanel, () -> Theme.footer("Calendário", AJUDA_CALENDARIO),
				"Não foi possível abrir o calendário de vendas.");
	}

	public static void showModificarProdutos() {
		showScreen(() -> new JScrollPane(ModifyProducts.getPanel()), () -> Theme.footer("Modificar produtos", AJUDA_MODIFICAR),
				"Não foi possível obter o catálogo de produtos.");
	}

	public static void showConfiguracoes() {
		showScreen(Settings::getSettingsPanel, () -> Theme.footer("Configurações", AJUDA_CONFIGURACOES),
				"Não foi possível abrir as configurações.");
	}

	/** Abre o WhatsApp Web no navegador padrão — atendimento ao cliente é direto por lá, não por uma tela própria no IMS. */
	public static void abrirWhatsAppWeb() {
		try {
			java.awt.Desktop.getDesktop().browse(new java.net.URI("https://web.whatsapp.com"));
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(frame,
					"Não foi possível abrir o navegador.\nERRO:" + ex.getMessage(),
					"ERRO", JOptionPane.ERROR_MESSAGE);
		}
	}

	public static void showAdminSaas() {
		showScreen(AdminSaas::getPanel, () -> Theme.footer("Acessos do site", AJUDA_ADMIN_SAAS),
				"Não foi possível abrir a tela de acesso administrativo.");
	}

	/** Abre a loja online (site) no navegador padrão, usando a URL configurada em Configurações. */
	public static void abrirLojaOnline() {
		String url;
		try {
			url = new Utility().getProperties().getOrDefault("saasAdminUrl", "");
		} catch (Exception ex) {
			url = "";
		}
		if (url == null || url.trim().isEmpty()) {
			JOptionPane.showMessageDialog(frame,
					"O endereço da loja online ainda não foi configurado.\nVá em Configurações e cole a URL do site.",
					"Loja Online", JOptionPane.WARNING_MESSAGE);
			return;
		}
		try {
			java.awt.Desktop.getDesktop().browse(new java.net.URI(url.trim()));
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(frame,
					"Não foi possível abrir o navegador.\nURL: " + url + "\nERRO:" + ex.getMessage(),
					"ERRO", JOptionPane.ERROR_MESSAGE);
		}
	}

	public static void main(String[] args) {
        Theme.applyLookAndFeel();
        SwingUtilities.updateComponentTreeUI(frame);
        SwingUtilities.updateComponentTreeUI(mb);

        final String dev_msg = "Sistema de Gerenciamento de Estoque (IMS) v1.0.8"
				+ "\n\nSoftware legado originalmente criado por Yash Modi."
				+ "\nAplicação atual continuada e desenvolvida por lectek."
				+ "\n\nAcesse https://github.com/lectek/Inventory_Management";
		frame.setTitle("Mini Mercadinho Rota o Melhor da Zona Sul");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1400, 800);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(Theme.BACKGROUND);
        frame.setJMenuBar(mb);

        try {
            new IMStart();

            m0i1.addActionListener(e -> showHome());
            m1i1.addActionListener(e -> showVenda());
            m2i1.addActionListener(e -> showProducao());
            m2i2.addActionListener(e -> showDespacho());
            m2i3.addActionListener(e -> showEstoque());
            m2i4.addActionListener(e -> showVendasPorDia());
            m2i5.addActionListener(e -> showCalendario());
            m2i6.addActionListener(e -> abrirWhatsAppWeb());
            m3i1.addActionListener(e -> showModificarProdutos());
            m3i2.addActionListener(e -> showAdminSaas());
            m4i1.addActionListener(e -> JOptionPane.showMessageDialog(frame, dev_msg, "Sobre o software", JOptionPane.INFORMATION_MESSAGE));
            m5i1.addActionListener(e -> showConfiguracoes());

            showHome();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Desculpe! Algo deu errado.\nERRO:" + e.getMessage(), "ERRO", JOptionPane.ERROR_MESSAGE);
        }
    }
}
