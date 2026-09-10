package br.com.lojagenerica.core.venda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lojagenerica.core.acesso.web.AuthController;
import br.com.lojagenerica.core.cadastro.FormaPagamento;
import br.com.lojagenerica.core.cadastro.FormaPagamentoRepository;
import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.cadastro.LocalEstoqueRepository;
import br.com.lojagenerica.core.cadastro.NaturezaFormaPagamento;
import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
import br.com.lojagenerica.core.estoque.MovimentacaoEstoqueService;
import br.com.lojagenerica.core.estoque.OrigemMovimentacao;
import br.com.lojagenerica.core.estoque.RegistrarMovimentacaoCommand;
import br.com.lojagenerica.core.estoque.SaldoEstoqueRepository;
import br.com.lojagenerica.core.produto.Produto;
import br.com.lojagenerica.core.produto.ProdutoRepository;
import br.com.lojagenerica.core.venda.web.VendaController;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Substitui PedidoEntity/StatusPedido — prova que registrar()+cancelar()
 * fecham o ciclo de estoque (saída na venda, entrada de volta no
 * cancelamento) e que ambos são idempotentes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class VendaFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

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
    private ProdutoRepository produtoRepository;
    @Autowired
    private FormaPagamentoRepository formaPagamentoRepository;
    @Autowired
    private SaldoEstoqueRepository saldoEstoqueRepository;
    @Autowired
    private MovimentacaoEstoqueService movimentacaoEstoqueService;

    private String schema;
    private Long produtoId;
    private Long localEstoqueId;
    private Long unidadeId;
    private Long formaPagamentoId;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void registrarVendaBaixaEstoqueECancelarDevolveDeFormaIdempotente() {
        String sufixo = "venda-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();
        prepararCadastrosComEstoqueInicial();

        String accessToken = login("dono@" + sufixo + ".example", "senhaForte123");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        UUID uuidVenda = UUID.randomUUID();
        VendaController.RegistrarVendaRequest request = new VendaController.RegistrarVendaRequest(
                uuidVenda, CanalVenda.PDV, localEstoqueId, null, null, null,
                List.of(new VendaController.ItemVendaRequest(produtoId, new BigDecimal("3"), unidadeId,
                        new BigDecimal("10.00"), null)),
                List.of(new VendaController.PagamentoVendaRequest(formaPagamentoId, new BigDecimal("30.00"),
                        new BigDecimal("30.00"), BigDecimal.ZERO)));

        ResponseEntity<VendaController.VendaResponse> criada = restTemplate.exchange(
                url("/api/v1/vendas"), HttpMethod.POST, new HttpEntity<>(request, headers),
                VendaController.VendaResponse.class);
        assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(criada.getBody().status()).isEqualTo("CONFIRMADA");
        assertThat(criada.getBody().total()).isEqualByComparingTo("30.00");
        Long vendaId = criada.getBody().id();

        assertSaldo("7"); // 10 iniciais - 3 vendidos

        // reenviar com o MESMO uuid é idempotente — não vende de novo
        ResponseEntity<VendaController.VendaResponse> repetida = restTemplate.exchange(
                url("/api/v1/vendas"), HttpMethod.POST, new HttpEntity<>(request, headers),
                VendaController.VendaResponse.class);
        assertThat(repetida.getBody().id()).isEqualTo(vendaId);
        assertSaldo("7");

        ResponseEntity<VendaController.VendaResponse> cancelada = restTemplate.exchange(
                url("/api/v1/vendas/" + vendaId + "/cancelar"), HttpMethod.POST,
                new HttpEntity<>(new VendaController.CancelarVendaRequest("cliente desistiu"), headers),
                VendaController.VendaResponse.class);
        assertThat(cancelada.getBody().status()).isEqualTo("CANCELADA");
        assertSaldo("10"); // devolveu

        // cancelar de novo é no-op — não devolve estoque duas vezes
        restTemplate.exchange(url("/api/v1/vendas/" + vendaId + "/cancelar"), HttpMethod.POST,
                new HttpEntity<>(new VendaController.CancelarVendaRequest("de novo"), headers),
                VendaController.VendaResponse.class);
        assertSaldo("10");
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

    private void prepararCadastrosComEstoqueInicial() {
        TenantContext.set(schema);
        try {
            UnidadeMedida unidade = unidadeMedidaRepository.save(new UnidadeMedida("UN", "Unidade", (short) 0, false));
            unidadeId = unidade.getId();
            localEstoqueId = localEstoqueRepository.save(new LocalEstoque("Loja", "LOJA", true)).getId();
            produtoId = produtoRepository.save(new Produto("Produto vendido", unidade)).getId();
            formaPagamentoId = formaPagamentoRepository.save(
                    new FormaPagamento("Dinheiro", NaturezaFormaPagamento.DINHEIRO, true)).getId();

            movimentacaoEstoqueService.registrar(new RegistrarMovimentacaoCommand(
                    produtoId, localEstoqueId, "INVENTARIO", br.com.lojagenerica.core.cadastro.SentidoMovimentacao.ENTRADA,
                    new BigDecimal("10"), unidadeId, null, OrigemMovimentacao.INVENTARIO, null, null, null, null,
                    "Estoque inicial de teste"));
        } finally {
            TenantContext.clear();
        }
    }

    private String login(String email, String senha) {
        ResponseEntity<AuthController.LoginResponse> resposta = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new AuthController.LoginRequest(email, senha, null),
                AuthController.LoginResponse.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody().accessToken();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
