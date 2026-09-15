package br.com.lojagenerica.application.service.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lojagenerica.adapters.inbound.web.controller.PublicCheckoutController;
import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.cadastro.LocalEstoqueRepository;
import br.com.lojagenerica.core.cadastro.NaturezaFormaPagamento;
import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
import br.com.lojagenerica.core.entrega.EntregaRotaService;
import br.com.lojagenerica.core.estoque.MovimentacaoEstoqueService;
import br.com.lojagenerica.core.estoque.OrigemMovimentacao;
import br.com.lojagenerica.core.estoque.RegistrarMovimentacaoCommand;
import br.com.lojagenerica.core.estoque.SaldoEstoqueRepository;
import br.com.lojagenerica.core.produto.ProdutoService;
import br.com.lojagenerica.core.venda.StatusVenda;
import br.com.lojagenerica.core.venda.Venda;
import br.com.lojagenerica.core.venda.VendaRepository;
import br.com.lojagenerica.domain.enums.ModoEntrega;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Checkout online reescrito na Fase C sobre {@link br.com.lojagenerica.core.venda.Venda}
 * — a venda nasce em RASCUNHO (carrinho -> pedido, sem tocar estoque) e só
 * confirma (baixa estoque) quando o pagamento é recebido. Dinheiro confirma
 * manualmente pelo staff; Pix/cartão dependeriam do Mercado Pago real (fora
 * do escopo deste teste — ver docs/ROADMAP.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CheckoutServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static HttpServer fakeNominatim;

    /** Mesmo padrão de Nominatim falso embutido usado em EntregaRotaFlowIT — evita rede real em teste. */
    @DynamicPropertySource
    static void propriedadesRota(DynamicPropertyRegistry registry) throws IOException {
        fakeNominatim = HttpServer.create(new InetSocketAddress(0), 0);
        fakeNominatim.createContext("/search", exchange -> {
            byte[] corpo = "[{\"lat\":\"-7.1195\",\"lon\":\"-34.8450\"}]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, corpo.length);
            try (var saida = exchange.getResponseBody()) {
                saida.write(corpo);
            }
        });
        fakeNominatim.start();
        registry.add("app.route.nominatim.base-url",
                () -> "http://localhost:" + fakeNominatim.getAddress().getPort() + "/search");
    }

    @AfterAll
    static void pararFakeNominatim() {
        if (fakeNominatim != null) {
            fakeNominatim.stop(0);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;
    @Autowired
    private UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired
    private LocalEstoqueRepository localEstoqueRepository;
    @Autowired
    private SaldoEstoqueRepository saldoEstoqueRepository;
    @Autowired
    private VendaRepository vendaRepository;
    @Autowired
    private CheckoutService checkoutService;
    @Autowired
    private ProdutoService produtoService;
    @Autowired
    private MovimentacaoEstoqueService movimentacaoEstoqueService;
    @Autowired
    private EntregaRotaService entregaRotaService;

    private String schema;
    private Long produtoId;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void checkoutEmDinheiroFicaPendenteEBaixaEstoqueSoQuandoStaffConfirmaRecebimento() {
        String sufixo = "checkout-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();
        prepararCadastros();

        var itemRequest = new PublicCheckoutController.ItemCarrinhoRequest(produtoId, new BigDecimal("2"));
        var request = new PublicCheckoutController.CriarPedidoRequest(
                "Cliente Teste", "cliente@" + sufixo + ".example", "83999999999",
                java.util.List.of(itemRequest), ModoEntrega.RETIRADA, null, NaturezaFormaPagamento.DINHEIRO);

        ResponseEntity<CheckoutService.CheckoutResultado> resposta = postPublico(
                "/api/public/pedidos", request, CheckoutService.CheckoutResultado.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long vendaId = resposta.getBody().pedidoId();
        assertThat(resposta.getBody().status()).isEqualTo("RASCUNHO");
        assertThat(resposta.getBody().checkoutUrl()).isNull(); // dinheiro não gera checkout online

        // Estoque ainda intacto — pagamento em dinheiro só confirma depois.
        assertSaldo("10");

        TenantContext.set(schema);
        try {
            assertThat(vendaRepository.findById(vendaId).orElseThrow().getStatus()).isEqualTo(StatusVenda.RASCUNHO);
        } finally {
            TenantContext.clear();
        }

        // Chamada direta ao service (não via HTTP admin, que hoje depende do
        // AdminJwtAuthFilter/admin_users antigos — ver docs/ROADMAP.md) precisa
        // do TenantContext setado manualmente, já que não passa por nenhum filtro.
        TenantContext.set(schema);
        try {
            checkoutService.confirmarRecebimentoDinheiro(vendaId);
        } finally {
            TenantContext.clear();
        }

        assertSaldo("8"); // 10 - 2

        TenantContext.set(schema);
        try {
            assertThat(vendaRepository.findById(vendaId).orElseThrow().getStatus()).isEqualTo(StatusVenda.CONFIRMADA);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Fecha o loop entre checkout e o módulo de entrega (core.entrega):
     * até esta rodada, {@code modoEntrega}/{@code enderecoEntrega} eram
     * capturados só pra calcular o frete e depois descartados — a venda
     * nunca ficava marcada como ENTREGA de verdade, então nunca aparecia
     * como elegível pra roteirização. Prova que hoje aparece.
     */
    @Test
    void checkoutEmModoEntregaCobraFreteEVendaFicaElegivelParaRoteirizacao() {
        String sufixo = "checkout-entrega-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();
        prepararCadastros();

        var itemRequest = new PublicCheckoutController.ItemCarrinhoRequest(produtoId, new BigDecimal("1"));
        var request = new PublicCheckoutController.CriarPedidoRequest(
                "Cliente Entrega", "cliente-entrega@" + sufixo + ".example", "83999999999",
                java.util.List.of(itemRequest), ModoEntrega.ENTREGA, "Rua das Flores, 100, João Pessoa",
                NaturezaFormaPagamento.DINHEIRO);

        ResponseEntity<CheckoutService.CheckoutResultado> resposta = postPublico(
                "/api/public/pedidos", request, CheckoutService.CheckoutResultado.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long vendaId = resposta.getBody().pedidoId();
        assertThat(resposta.getBody().valorFrete()).isGreaterThan(BigDecimal.ZERO);

        TenantContext.set(schema);
        try {
            Venda venda = vendaRepository.findById(vendaId).orElseThrow();
            assertThat(venda.getModoEntrega()).isEqualTo(ModoEntrega.ENTREGA);
            assertThat(venda.getEnderecoEntrega()).isEqualTo("Rua das Flores, 100, João Pessoa");
            assertThat(venda.getValorFrete()).isEqualByComparingTo(resposta.getBody().valorFrete());
            // Ainda em RASCUNHO: não aparece como elegível antes de o pagamento ser confirmado.
            assertThat(entregaRotaService.listarVendasElegiveis()).extracting(Venda::getId).doesNotContain(vendaId);
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(schema);
        try {
            checkoutService.confirmarRecebimentoDinheiro(vendaId);
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(schema);
        try {
            assertThat(entregaRotaService.listarVendasElegiveis()).extracting(Venda::getId).contains(vendaId);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void checkoutOnlineSemAccessTokenConfiguradoFalhaAoTentarGerarPix() {
        String sufixo = "checkout-pix-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();
        prepararCadastros();

        var itemRequest = new PublicCheckoutController.ItemCarrinhoRequest(produtoId, new BigDecimal("1"));
        var request = new PublicCheckoutController.CriarPedidoRequest(
                "Cliente Pix", "cliente-pix@" + sufixo + ".example", null,
                java.util.List.of(itemRequest), ModoEntrega.RETIRADA, null, NaturezaFormaPagamento.PIX);

        // IllegalStateException ("MP não configurado") -> RestExceptionTranslator mapeia pra 409,
        // não 500 — ver adapters/inbound/web/advice/RestExceptionTranslator.
        ResponseEntity<String> resposta = postPublico("/api/public/pedidos", request, String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** Toda request pra /api/public/** precisa do header X-Empresa — sem subdomínio real disponível em teste. */
    private <T> ResponseEntity<T> postPublico(String path, Object body, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Empresa", schema);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
    }

    private void assertSaldo(String esperado) {
        TenantContext.set(schema);
        try {
            assertThat(saldoEstoqueRepository.findByProdutoId(produtoId)).hasSize(1);
            assertThat(saldoEstoqueRepository.findByProdutoId(produtoId).get(0).getQuantidade())
                    .isEqualByComparingTo(esperado);
        } finally {
            TenantContext.clear();
        }
    }

    private void prepararCadastros() {
        TenantContext.set(schema);
        try {
            UnidadeMedida unidade = unidadeMedidaRepository.save(new UnidadeMedida("UN", "Unidade", (short) 0, false));
            Long localId = localEstoqueRepository.save(new LocalEstoque("Loja Online", "LOJA", true)).getId();
            produtoId = produtoService.criarComDadosIniciais(
                    "Produto do site", unidade.getId(), null, null, new BigDecimal("15.00")).getId();
            movimentacaoEstoqueService.registrar(new RegistrarMovimentacaoCommand(
                    produtoId, localId, "INVENTARIO", br.com.lojagenerica.core.cadastro.SentidoMovimentacao.ENTRADA,
                    new BigDecimal("10"), unidade.getId(), null, OrigemMovimentacao.INVENTARIO, null, null, null, null,
                    "Estoque inicial de teste"));
        } finally {
            TenantContext.clear();
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
