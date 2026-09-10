package br.com.lojagenerica.core.compra;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lojagenerica.core.acesso.web.AuthController;
import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.cadastro.LocalEstoqueRepository;
import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
import br.com.lojagenerica.core.compra.web.CompraController;
import br.com.lojagenerica.core.estoque.SaldoEstoqueRepository;
import br.com.lojagenerica.core.parceiro.Fornecedor;
import br.com.lojagenerica.core.parceiro.FornecedorRepository;
import br.com.lojagenerica.core.produto.Produto;
import br.com.lojagenerica.core.produto.ProdutoRepository;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
import java.math.BigDecimal;
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
 * Fluxo completo do spec: FORNECEDOR -> COMPRA -> ESTOQUE -> PRODUTO
 * (custo atualizado). Confirma() é o primeiro template de transação
 * cross-módulo do sistema — este teste prova que ele funciona ponta a
 * ponta e é idempotente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CompraFlowIT {

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
    private FornecedorRepository fornecedorRepository;
    @Autowired
    private SaldoEstoqueRepository saldoEstoqueRepository;

    private String schema;
    private Long produtoId;
    private Long fornecedorId;
    private Long localEstoqueId;
    private Long unidadeId;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void confirmarCompraGeraEstoqueEAtualizaCustoDeFormaIdempotente() {
        String sufixo = "compra-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();
        prepararCadastros();

        String accessToken = login("dono@" + sufixo + ".example", "senhaForte123");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        Long compraId = restTemplate.exchange(
                url("/api/v1/compras"), HttpMethod.POST,
                new HttpEntity<>(new CompraController.CriarCompraRequest(fornecedorId, localEstoqueId), headers),
                CompraController.CompraResponse.class).getBody().id();

        restTemplate.exchange(
                url("/api/v1/compras/" + compraId + "/itens"), HttpMethod.POST,
                new HttpEntity<>(new CompraController.AdicionarItemRequest(
                        produtoId, new BigDecimal("100"), unidadeId, new BigDecimal("5.00"), null), headers),
                CompraController.CompraResponse.class);

        ResponseEntity<CompraController.CompraResponse> comCondicoes = restTemplate.exchange(
                url("/api/v1/compras/" + compraId + "/condicoes"), HttpMethod.POST,
                new HttpEntity<>(new CompraController.DefinirCondicoesRequest(
                        null, null, new BigDecimal("50.00"), null, null), headers),
                CompraController.CompraResponse.class);
        // subtotal 500 + frete 50 = 550
        assertThat(comCondicoes.getBody().total()).isEqualByComparingTo("550.00");

        ResponseEntity<CompraController.CompraResponse> confirmada = restTemplate.exchange(
                url("/api/v1/compras/" + compraId + "/confirmar"), HttpMethod.POST,
                new HttpEntity<>(headers), CompraController.CompraResponse.class);
        assertThat(confirmada.getBody().status()).isEqualTo("CONFIRMADA");
        assertThat(confirmada.getBody().confirmadaEm()).isNotNull();

        TenantContext.set(schema);
        try {
            assertThat(saldoEstoqueRepository.findByProdutoId(produtoId)).hasSize(1);
            assertThat(saldoEstoqueRepository.findByProdutoId(produtoId).get(0).getQuantidade())
                    .isEqualByComparingTo("100");
            // custo = (5.00*100 + rateio de frete 50) / 100 = 5.50
            assertThat(saldoEstoqueRepository.findByProdutoId(produtoId).get(0).getCustoMedio())
                    .isEqualByComparingTo("5.50");

            Produto produto = produtoRepository.findById(produtoId).orElseThrow();
            assertThat(produto.getCustoAquisicao()).isEqualByComparingTo("5.50");
        } finally {
            TenantContext.clear();
        }

        // confirmar de novo é no-op — não duplica estoque
        ResponseEntity<CompraController.CompraResponse> reconfirmada = restTemplate.exchange(
                url("/api/v1/compras/" + compraId + "/confirmar"), HttpMethod.POST,
                new HttpEntity<>(headers), CompraController.CompraResponse.class);
        assertThat(reconfirmada.getStatusCode()).isEqualTo(HttpStatus.OK);

        TenantContext.set(schema);
        try {
            assertThat(saldoEstoqueRepository.findByProdutoId(produtoId).get(0).getQuantidade())
                    .isEqualByComparingTo("100");
        } finally {
            TenantContext.clear();
        }
    }

    private void prepararCadastros() {
        TenantContext.set(schema);
        try {
            UnidadeMedida unidade = unidadeMedidaRepository.save(new UnidadeMedida("UN", "Unidade", (short) 0, false));
            unidadeId = unidade.getId();
            localEstoqueId = localEstoqueRepository.save(new LocalEstoque("Depósito", "DEPOSITO", true)).getId();
            produtoId = produtoRepository.save(new Produto("Produto comprado", unidade)).getId();
            fornecedorId = fornecedorRepository.save(new Fornecedor("Fornecedor Teste LTDA")).getId();
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
