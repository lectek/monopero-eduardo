package br.com.lojagenerica.pdv;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.core.acesso.web.AuthController;
import br.com.lojagenerica.core.cadastro.FormaPagamento;
import br.com.lojagenerica.core.cadastro.FormaPagamentoRepository;
import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.cadastro.LocalEstoqueRepository;
import br.com.lojagenerica.core.cadastro.NaturezaFormaPagamento;
import br.com.lojagenerica.core.cadastro.SentidoMovimentacao;
import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
import br.com.lojagenerica.core.estoque.MovimentacaoEstoqueService;
import br.com.lojagenerica.core.estoque.OrigemMovimentacao;
import br.com.lojagenerica.core.estoque.RegistrarMovimentacaoCommand;
import br.com.lojagenerica.core.estoque.SaldoEstoqueRepository;
import br.com.lojagenerica.core.produto.Produto;
import br.com.lojagenerica.core.produto.ProdutoRepository;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.pdv.web.PdvSyncController;
import br.com.lojagenerica.pdv.web.TerminalController;
import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prova a fatia servidor do PDV offline (Fase D): pareamento de terminal
 * autenticado por JWT de usuário, push de eventos autenticado por chave de
 * API do terminal (sem usuário logado), idempotência por evento_uuid, e
 * pull incremental de catálogo por cursor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PdvSyncFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
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
    @Autowired
    private UsuarioRepository usuarioRepository;

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
    void pareiaTerminalEmpurraVendaIdempotenteECancelaEPuxaCatalogoPorCursor() {
        String sufixo = "pdv-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();
        prepararCadastrosComEstoqueInicial();

        String accessToken = login("dono@" + sufixo + ".example", "senhaForte123");
        HttpHeaders jwtHeaders = new HttpHeaders();
        jwtHeaders.setBearerAuth(accessToken);

        ResponseEntity<TerminalService.TerminalCriadoResultado> pareado = restTemplate.exchange(
                url("/api/v1/pdv/terminais"), HttpMethod.POST,
                new HttpEntity<>(new TerminalController.CriarTerminalRequest("Caixa 1"), jwtHeaders),
                TerminalService.TerminalCriadoResultado.class);
        assertThat(pareado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String apiKey = pareado.getBody().apiKey();
        assertThat(apiKey).startsWith(schema + ".");

        HttpHeaders terminalHeaders = new HttpHeaders();
        terminalHeaders.add("X-Terminal-Api-Key", apiKey);

        UUID uuidVenda = UUID.randomUUID();
        VendaRegistradaPayload payload = new VendaRegistradaPayload(localEstoqueId, null, null, null,
                List.of(new VendaRegistradaPayload.ItemPayload(produtoId, new BigDecimal("3"), unidadeId,
                        new BigDecimal("10.00"), null)),
                List.of(new VendaRegistradaPayload.PagamentoPayload(formaPagamentoId, new BigDecimal("30.00"),
                        new BigDecimal("30.00"), BigDecimal.ZERO)));
        EventoPushRequest eventoVenda = new EventoPushRequest(uuidVenda, TipoEventoPdv.VENDA_REGISTRADA,
                Instant.now(), paraMapa(payload));

        ResponseEntity<PdvSyncController.PushResponse> pushVenda = push(terminalHeaders, eventoVenda);
        assertThat(pushVenda.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pushVenda.getBody().resultados()).hasSize(1);
        ResultadoEventoResponse resultadoVenda = pushVenda.getBody().resultados().get(0);
        assertThat(resultadoVenda.status()).isEqualTo(ResultadoEventoResponse.StatusEvento.ACEITO);
        assertThat(resultadoVenda.servidorId()).isNotNull();
        Long vendaId = resultadoVenda.servidorId();

        assertSaldo("7"); // 10 iniciais - 3 vendidos

        // reenviar o MESMO evento (reconexão no meio do envio) não vende de novo
        ResponseEntity<PdvSyncController.PushResponse> pushRepetido = push(terminalHeaders, eventoVenda);
        ResultadoEventoResponse resultadoRepetido = pushRepetido.getBody().resultados().get(0);
        assertThat(resultadoRepetido.status()).isEqualTo(ResultadoEventoResponse.StatusEvento.DUPLICADO);
        assertThat(resultadoRepetido.servidorId()).isEqualTo(vendaId);
        assertSaldo("7");

        // cancela a venda por uuid, autenticado só com a chave do terminal (sem usuário logado)
        VendaCanceladaPayload payloadCancelamento = new VendaCanceladaPayload(uuidVenda, "cliente desistiu", null);
        EventoPushRequest eventoCancelamento = new EventoPushRequest(UUID.randomUUID(), TipoEventoPdv.VENDA_CANCELADA,
                Instant.now(), paraMapa(payloadCancelamento));
        ResponseEntity<PdvSyncController.PushResponse> pushCancelamento = push(terminalHeaders, eventoCancelamento);
        ResultadoEventoResponse resultadoCancelamento = pushCancelamento.getBody().resultados().get(0);
        assertThat(resultadoCancelamento.status()).isEqualTo(ResultadoEventoResponse.StatusEvento.ACEITO);
        assertSaldo("10"); // devolveu

        // pull incremental de produto, por cursor
        ResponseEntity<PullResponseProduto> primeiraPagina = pull(terminalHeaders, null);
        assertThat(primeiraPagina.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(primeiraPagina.getBody().itens()).extracting(ProdutoSyncDTO::id).contains(produtoId);
        String cursor = primeiraPagina.getBody().proximoCursor();

        TenantContext.set(schema);
        Long produtoNovoId;
        try {
            produtoNovoId = produtoRepository.save(new Produto("Produto novo pós-cursor",
                    unidadeMedidaRepository.findById(unidadeId).orElseThrow())).getId();
        } finally {
            TenantContext.clear();
        }

        ResponseEntity<PullResponseProduto> segundaPagina = pull(terminalHeaders, cursor);
        assertThat(segundaPagina.getBody().itens()).extracting(ProdutoSyncDTO::id)
                .containsExactly(produtoNovoId);

        // forma_pagamento e local_estoque são cadastros pequenos — sem cursor, lista inteira sempre
        ResponseEntity<PullResponseFormaPagamento> formasPagamento = restTemplate.exchange(
                UriComponentsBuilder.fromHttpUrl(url("/api/v1/pdv/sync/pull")).queryParam("recurso", "forma_pagamento")
                        .toUriString(),
                HttpMethod.GET, new HttpEntity<>(terminalHeaders), PullResponseFormaPagamento.class);
        assertThat(formasPagamento.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(formasPagamento.getBody().itens()).extracting(FormaPagamentoSyncDTO::id).contains(formaPagamentoId);
        assertThat(formasPagamento.getBody().temMais()).isFalse();

        ResponseEntity<PullResponseLocalEstoque> locaisEstoque = restTemplate.exchange(
                UriComponentsBuilder.fromHttpUrl(url("/api/v1/pdv/sync/pull")).queryParam("recurso", "local_estoque")
                        .toUriString(),
                HttpMethod.GET, new HttpEntity<>(terminalHeaders), PullResponseLocalEstoque.class);
        assertThat(locaisEstoque.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(locaisEstoque.getBody().itens()).extracting(LocalEstoqueSyncDTO::id).contains(localEstoqueId);
    }

    /**
     * Regressão: o payload do PDV só carrega usuarioId (não e-mail), mas
     * VendaService.validarDesconto checa permissão pelo e-mail — sem
     * resolver usuarioId -> email dentro de PdvSyncService, qualquer
     * desconto vindo do caixa falhava com "usuário não identificado",
     * mesmo pro dono (ADMINISTRADOR).
     */
    @Test
    void vendaComDescontoResolveEmailDoUsuarioAPartirDoId() {
        String sufixo = "pdv-desc-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();
        prepararCadastrosComEstoqueInicial();

        String accessToken = login("dono@" + sufixo + ".example", "senhaForte123");
        HttpHeaders jwtHeaders = new HttpHeaders();
        jwtHeaders.setBearerAuth(accessToken);
        ResponseEntity<TerminalService.TerminalCriadoResultado> pareado = restTemplate.exchange(
                url("/api/v1/pdv/terminais"), HttpMethod.POST,
                new HttpEntity<>(new TerminalController.CriarTerminalRequest("Caixa 1"), jwtHeaders),
                TerminalService.TerminalCriadoResultado.class);
        String apiKey = pareado.getBody().apiKey();

        TenantContext.set(schema);
        Long donoId;
        try {
            donoId = usuarioRepository.findByEmailIgnoreCase("dono@" + sufixo + ".example").orElseThrow().getId();
        } finally {
            TenantContext.clear();
        }

        HttpHeaders terminalHeaders = new HttpHeaders();
        terminalHeaders.add("X-Terminal-Api-Key", apiKey);

        VendaRegistradaPayload payload = new VendaRegistradaPayload(localEstoqueId, null, donoId, new BigDecimal("5.00"),
                List.of(new VendaRegistradaPayload.ItemPayload(produtoId, new BigDecimal("3"), unidadeId,
                        new BigDecimal("10.00"), null)),
                List.of(new VendaRegistradaPayload.PagamentoPayload(formaPagamentoId, new BigDecimal("25.00"),
                        new BigDecimal("25.00"), BigDecimal.ZERO)));
        EventoPushRequest evento = new EventoPushRequest(UUID.randomUUID(), TipoEventoPdv.VENDA_REGISTRADA,
                Instant.now(), paraMapa(payload));

        ResponseEntity<PdvSyncController.PushResponse> resultado = push(terminalHeaders, evento);

        assertThat(resultado.getBody().resultados().get(0).status())
                .isEqualTo(ResultadoEventoResponse.StatusEvento.ACEITO);
    }

    private Map<String, Object> paraMapa(Object payload) {
        return objectMapper.convertValue(payload, new TypeReference<>() {
        });
    }

    private ResponseEntity<PdvSyncController.PushResponse> push(HttpHeaders terminalHeaders, EventoPushRequest evento) {
        return restTemplate.exchange(url("/api/v1/pdv/sync/push"), HttpMethod.POST,
                new HttpEntity<>(new PdvSyncController.PushRequest(List.of(evento)), terminalHeaders),
                PdvSyncController.PushResponse.class);
    }

    private ResponseEntity<PullResponseProduto> pull(HttpHeaders terminalHeaders, String desde) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url("/api/v1/pdv/sync/pull"))
                .queryParam("recurso", "produto");
        if (desde != null) {
            builder.queryParam("desde", desde);
        }
        return restTemplate.exchange(builder.toUriString(), HttpMethod.GET,
                new HttpEntity<>(terminalHeaders), PullResponseProduto.class);
    }

    /** Testcontainers/Jackson não deserializam o `PullResponse<T>` genérico direto — DTO concreto só pro teste. */
    private record PullResponseProduto(List<ProdutoSyncDTO> itens, String proximoCursor, boolean temMais) {
    }

    private record PullResponseFormaPagamento(List<FormaPagamentoSyncDTO> itens, String proximoCursor, boolean temMais) {
    }

    private record PullResponseLocalEstoque(List<LocalEstoqueSyncDTO> itens, String proximoCursor, boolean temMais) {
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
                    produtoId, localEstoqueId, "INVENTARIO", SentidoMovimentacao.ENTRADA,
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
