package br.com.lojagenerica.core.estoque;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lojagenerica.core.acesso.web.AuthController;
import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.cadastro.LocalEstoqueRepository;
import br.com.lojagenerica.core.cadastro.SentidoMovimentacao;
import br.com.lojagenerica.core.cadastro.TipoMovimentacao;
import br.com.lojagenerica.core.cadastro.TipoMovimentacaoRepository;
import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
import br.com.lojagenerica.core.estoque.web.EstoqueController;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EstoqueMovimentacaoFlowIT {

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
    private TipoMovimentacaoRepository tipoMovimentacaoRepository;
    @Autowired
    private ProdutoRepository produtoRepository;
    @Autowired
    private SaldoEstoqueRepository saldoEstoqueRepository;

    private String schema;
    private Long produtoId;
    private Long localEstoqueId;
    private Long unidadeId;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void ajusteManualDeEstoqueExigeMotivoEAtualizaSaldo() {
        String sufixo = "estoque-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();

        prepararCadastros();

        String accessToken = login("dono@" + sufixo + ".example", "senhaForte123");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<EstoqueController.MovimentacaoResponse> resposta = restTemplate.exchange(
                url("/api/v1/estoque/movimentacoes"), HttpMethod.POST,
                new HttpEntity<>(new EstoqueController.AjusteManualRequest(
                        produtoId, localEstoqueId, "PERDA", SentidoMovimentacao.SAIDA,
                        new BigDecimal("3"), unidadeId, "produto quebrado no transporte"), headers),
                EstoqueController.MovimentacaoResponse.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody().saldoApos()).isEqualByComparingTo("-3");

        ResponseEntity<EstoqueController.SaldoResponse[]> saldos = restTemplate.exchange(
                url("/api/v1/estoque/saldos?produtoId=" + produtoId), HttpMethod.GET, new HttpEntity<>(headers),
                EstoqueController.SaldoResponse[].class);
        assertThat(saldos.getBody()).hasSize(1);
        assertThat(saldos.getBody()[0].quantidade()).isEqualByComparingTo("-3");

        ResponseEntity<EstoqueController.MovimentacaoResponse[]> movimentacoes = restTemplate.exchange(
                url("/api/v1/estoque/produtos/" + produtoId + "/movimentacoes"), HttpMethod.GET,
                new HttpEntity<>(headers), EstoqueController.MovimentacaoResponse[].class);
        assertThat(movimentacoes.getBody()).hasSize(1);

        TenantContext.set(schema);
        try {
            assertThat(saldoEstoqueRepository.findByProdutoId(produtoId)).hasSize(1);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void ajusteManualSemMotivoEhRejeitado() {
        String sufixo = "estoque-sem-motivo-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();
        prepararCadastros();

        String accessToken = login("dono@" + sufixo + ".example", "senhaForte123");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        // motivo em branco -> @NotBlank do request rejeita com 400 antes de chegar no service
        ResponseEntity<String> resposta = restTemplate.exchange(
                url("/api/v1/estoque/movimentacoes"), HttpMethod.POST,
                new HttpEntity<>("{\"produtoId\":" + produtoId + ",\"localEstoqueId\":" + localEstoqueId
                        + ",\"tipoMovimentacaoCodigo\":\"PERDA\",\"sentido\":\"SAIDA\",\"quantidade\":1,"
                        + "\"unidadeId\":" + unidadeId + ",\"motivo\":\"\"}", headers),
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void prepararCadastros() {
        TenantContext.set(schema);
        try {
            UnidadeMedida unidade = unidadeMedidaRepository.save(new UnidadeMedida("UN", "Unidade", (short) 0, false));
            unidadeId = unidade.getId();
            localEstoqueId = localEstoqueRepository.save(new LocalEstoque("Depósito Principal", "DEPOSITO", true)).getId();
            tipoMovimentacaoRepository.save(new TipoMovimentacao(
                    "PERDA", "Perda", SentidoMovimentacao.SAIDA, false, true, false));
            produtoId = produtoRepository.save(new Produto("Produto de teste", unidade)).getId();
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
