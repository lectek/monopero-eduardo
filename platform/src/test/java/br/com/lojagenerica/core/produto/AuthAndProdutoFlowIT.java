package br.com.lojagenerica.core.produto;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lojagenerica.core.acesso.Papel;
import br.com.lojagenerica.core.acesso.PapelRepository;
import br.com.lojagenerica.core.acesso.Permissao;
import br.com.lojagenerica.core.acesso.PermissaoRepository;
import br.com.lojagenerica.core.acesso.Usuario;
import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.core.acesso.web.AuthController;
import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
import br.com.lojagenerica.core.produto.web.ProdutoController;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.IdentidadeService;
import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
import br.com.lojagenerica.platform.domain.EmpresaRepository;
import br.com.lojagenerica.platform.domain.IdentidadeUsuario;
import br.com.lojagenerica.platform.domain.IdentidadeUsuarioRepository;
import java.math.BigDecimal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
 * Fatia vertical ponta a ponta: provisiona empresa -> login resolve tenant ->
 * JWT carrega permissões -> @PreAuthorize nos endpoints de /api/v1/produtos
 * -> alteração de preço grava histórico. Passa pelo filtro real
 * (JwtAuthenticationFilter), não chama os beans direto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthAndProdutoFlowIT {

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
    private ProdutoPrecoHistoricoRepository precoHistoricoRepository;
    @Autowired
    private PapelRepository papelRepository;
    @Autowired
    private PermissaoRepository permissaoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private IdentidadeUsuarioRepository identidadeUsuarioRepository;

    private String schemaEmpresa;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void loginResolveTenantEPermiteCrudDeProdutoComAuditoriaDePreco() {
        String sufixo = "flow-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schemaEmpresa = empresa.getSchemaNome();

        Long unidadeId = criarUnidadeMedida();

        // sem token -> 401
        ResponseEntity<String> semToken = restTemplate.getForEntity(url("/api/v1/produtos"), String.class);
        assertThat(semToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String accessToken = login("dono@" + sufixo + ".example", "senhaForte123");
        assertThat(accessToken).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<ProdutoController.ProdutoResponse> criado = restTemplate.exchange(
                url("/api/v1/produtos"), HttpMethod.POST,
                new HttpEntity<>(new ProdutoController.CriarProdutoRequest("Produto de teste", unidadeId), headers),
                ProdutoController.ProdutoResponse.class);
        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long produtoId = criado.getBody().id();

        ResponseEntity<ProdutoController.ProdutoResponse[]> listagem = restTemplate.exchange(
                url("/api/v1/produtos"), HttpMethod.GET, new HttpEntity<>(headers),
                ProdutoController.ProdutoResponse[].class);
        assertThat(listagem.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listagem.getBody()).extracting(ProdutoController.ProdutoResponse::nome).contains("Produto de teste");

        ResponseEntity<ProdutoController.ProdutoResponse> alterado = restTemplate.exchange(
                url("/api/v1/produtos/" + produtoId + "/preco"), HttpMethod.PATCH,
                new HttpEntity<>(new ProdutoController.AlterarPrecoRequest(
                        new BigDecimal("19.90"), new BigDecimal("10.00"), "preço inicial"), headers),
                ProdutoController.ProdutoResponse.class);
        assertThat(alterado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(alterado.getBody().precoVenda()).isEqualByComparingTo("19.90");

        TenantContext.set(schemaEmpresa);
        try {
            assertThat(precoHistoricoRepository.findByProdutoIdOrderByVigenteDeDesc(produtoId)).hasSize(1);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Regressão: {@code @PreAuthorize} negando acesso (usuário autenticado,
     * sem a permissão exigida) caía no handler genérico de
     * {@code RestExceptionTranslator} e virava HTTP 500 — em QUALQUER
     * endpoint de {@code /api/v1/**}, não só nas telas de {@code /gestao/**}
     * (onde o mesmo bug foi encontrado). Prova que agora vira 403 de
     * verdade, com um usuário que só tem PRODUTO_LER tentando criar produto
     * (exige PRODUTO_ESCREVER).
     */
    @Test
    void preAuthorizeNegaAcessoComHttp403NaoComHttp500() {
        String sufixo = "flow-403-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schemaEmpresa = empresa.getSchemaNome();
        Long unidadeId = criarUnidadeMedida();

        String emailLeitor = "leitor@" + sufixo + ".example";
        TenantContext.set(schemaEmpresa);
        try {
            Permissao produtoLer = permissaoRepository.findByCodigo("PRODUTO_LER").orElseThrow();
            Papel leitor = papelRepository.save(new Papel("Leitor de produtos", "Só leitura", false));
            leitor.getPermissoes().add(produtoLer);
            leitor = papelRepository.save(leitor);
            Usuario usuario = new Usuario("Usuário Leitor", emailLeitor);
            usuario.getPapeis().add(leitor);
            usuario = usuarioRepository.save(usuario);

            TenantContext.clear();
            Empresa empresaAtual = empresaRepository.findBySchemaNome(schemaEmpresa).orElseThrow();
            identidadeUsuarioRepository.save(new IdentidadeUsuario(emailLeitor, empresaAtual.getId(), usuario.getId(),
                    new BCryptPasswordEncoder().encode("senhaDoLeitor123")));
        } finally {
            TenantContext.clear();
        }

        String tokenLeitor = login(emailLeitor, "senhaDoLeitor123");
        HttpHeaders headersLeitor = new HttpHeaders();
        headersLeitor.setBearerAuth(tokenLeitor);

        // Lê: permitido.
        ResponseEntity<ProdutoController.ProdutoResponse[]> listagem = restTemplate.exchange(
                url("/api/v1/produtos"), HttpMethod.GET, new HttpEntity<>(headersLeitor),
                ProdutoController.ProdutoResponse[].class);
        assertThat(listagem.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Escreve: negado com 403 (não 500).
        ResponseEntity<String> criacaoNegada = restTemplate.exchange(
                url("/api/v1/produtos"), HttpMethod.POST,
                new HttpEntity<>(new ProdutoController.CriarProdutoRequest("Não devia existir", unidadeId), headersLeitor),
                String.class);
        assertThat(criacaoNegada.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private Long criarUnidadeMedida() {
        TenantContext.set(schemaEmpresa);
        try {
            return unidadeMedidaRepository.save(new UnidadeMedida("UN", "Unidade", (short) 0, false)).getId();
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
