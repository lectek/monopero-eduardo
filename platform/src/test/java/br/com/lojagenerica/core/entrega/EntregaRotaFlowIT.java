package br.com.lojagenerica.core.entrega;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lojagenerica.application.core.settings.AppSettingService;
import br.com.lojagenerica.core.acesso.Papel;
import br.com.lojagenerica.core.acesso.PapelRepository;
import br.com.lojagenerica.core.acesso.Permissao;
import br.com.lojagenerica.core.acesso.PermissaoRepository;
import br.com.lojagenerica.core.acesso.Usuario;
import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.core.cadastro.FormaPagamento;
import br.com.lojagenerica.core.cadastro.FormaPagamentoRepository;
import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.cadastro.LocalEstoqueRepository;
import br.com.lojagenerica.core.cadastro.NaturezaFormaPagamento;
import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
import br.com.lojagenerica.core.produto.Produto;
import br.com.lojagenerica.core.produto.ProdutoRepository;
import br.com.lojagenerica.core.venda.CanalVenda;
import br.com.lojagenerica.core.venda.RegistrarVendaCommand;
import br.com.lojagenerica.core.venda.VendaService;
import br.com.lojagenerica.domain.enums.ModoEntrega;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
import br.com.lojagenerica.platform.domain.EmpresaRepository;
import br.com.lojagenerica.platform.domain.IdentidadeUsuario;
import br.com.lojagenerica.platform.domain.IdentidadeUsuarioRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fatia ponta a ponta do módulo de entrega: admin roteiriza vendas em modo
 * ENTREGA (TSP via {@code DeliveryRouteService}, geocodificação contra um
 * Nominatim falso embutido — mesmo padrão de teste encontrado em
 * multlektec/DeliveryRouteServiceTest e já usado nesta suíte pra
 * PdvSyncFlowIT/ApiClientTest), motoboy reivindica a rota, executa as
 * paradas em sequência e vê a comissão. Prova também a reivindicação
 * atômica: um segundo motoboy não consegue assumir a mesma rota.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class EntregaRotaFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static HttpServer fakeNominatim;

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

    @Autowired
    private MockMvc mockMvc;
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
    private VendaService vendaService;
    @Autowired
    private PapelRepository papelRepository;
    @Autowired
    private PermissaoRepository permissaoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private AppSettingService appSettingService;
    @Autowired
    private EntregaRotaRepository entregaRotaRepository;
    @Autowired
    private EntregaParadaRepository entregaParadaRepository;
    @Autowired
    private EntregaOcorrenciaRepository entregaOcorrenciaRepository;
    @Autowired
    private EntregaRotaService entregaRotaService;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private IdentidadeUsuarioRepository identidadeUsuarioRepository;

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private String schema;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void roteirizaVendasMotoboyExecutaEmSequenciaEComissaoEReivindicacaoAtomica() throws Exception {
        String sufixo = "entrega-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();

        TenantContext.set(schema);
        Long vendaId1;
        Long vendaId2;
        Long vendaId3;
        Long vendaId4;
        String emailMotoboy1 = "motoboy1@" + sufixo + ".example";
        String emailMotoboy2 = "motoboy2@" + sufixo + ".example";
        Long papelMotoboyId;
        try {
            appSettingService.upsert("entrega.motoboy.comissao_percentual", "80", "Comissão do motoboy");

            Long unidadeId = unidadeMedidaRepository.save(new UnidadeMedida("UN", "Unidade", (short) 0, false)).getId();
            Long localId = localEstoqueRepository.save(new LocalEstoque("Loja", "LOJA", true)).getId();
            Long produtoId = produtoRepository.save(new Produto("Produto entregável",
                    unidadeMedidaRepository.findById(unidadeId).orElseThrow())).getId();
            Long formaPagamentoId = formaPagamentoRepository.save(
                    new FormaPagamento("Dinheiro", NaturezaFormaPagamento.DINHEIRO, true)).getId();

            vendaId1 = criarVendaEmEntrega(localId, produtoId, unidadeId, formaPagamentoId,
                    "Rua das Flores, 100, João Pessoa", new BigDecimal("8.00"));
            vendaId2 = criarVendaEmEntrega(localId, produtoId, unidadeId, formaPagamentoId,
                    "Rua das Palmeiras, 200, João Pessoa", new BigDecimal("12.00"));
            vendaId3 = criarVendaEmEntrega(localId, produtoId, unidadeId, formaPagamentoId,
                    "Rua dos Coqueiros, 300, João Pessoa", new BigDecimal("9.00"));
            vendaId4 = criarVendaEmEntrega(localId, produtoId, unidadeId, formaPagamentoId,
                    "Rua do Sol, 400, João Pessoa", new BigDecimal("11.00"));

            Permissao entregaExecutar = permissaoRepository.findByCodigo("ENTREGA_EXECUTAR").orElseThrow();
            Papel papelMotoboy = papelRepository.save(new Papel("Motoboy", "Executa rotas de entrega", false));
            papelMotoboy.getPermissoes().add(entregaExecutar);
            papelMotoboy = papelRepository.save(papelMotoboy);
            papelMotoboyId = papelMotoboy.getId();

            criarUsuarioComPapel(emailMotoboy1, "Motoboy Um", papelMotoboyId);
            criarUsuarioComPapel(emailMotoboy2, "Motoboy Dois", papelMotoboyId);
        } finally {
            TenantContext.clear();
        }
        Empresa empresaAtual = empresaRepository.findBySchemaNome(schema).orElseThrow();
        criarIdentidade(empresaAtual, emailMotoboy1);
        criarIdentidade(empresaAtual, emailMotoboy2);

        MockHttpSession sessaoDono = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(sessaoDono).with(csrf())
                        .param("email", "dono@" + sufixo + ".example")
                        .param("senha", "senhaForte123"))
                .andExpect(status().is3xxRedirection());

        MockHttpSession sessaoMotoboy1 = login(emailMotoboy1, "senhaMotoboy123");
        MockHttpSession sessaoMotoboy2 = login(emailMotoboy2, "senhaMotoboy123");

        // Dono pré-visualiza e cria a rota.
        var criacao = mockMvc.perform(post("/gestao/entregas").session(sessaoDono).with(csrf())
                        .param("vendaIds", String.valueOf(vendaId1), String.valueOf(vendaId2))
                        .param("origem", "Loja Central, João Pessoa"))
                .andExpect(redirectedUrlPattern("/gestao/entregas/rotas/*"))
                .andReturn();
        String location = criacao.getResponse().getRedirectedUrl();
        Long rotaId = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(get("/gestao/entregas/rotas/{id}", rotaId).session(sessaoDono))
                .andExpect(status().isOk());

        // Motoboy 2 não vê a rota como "não disponível" ainda -- os dois veem, mas só um consegue reivindicar.
        mockMvc.perform(get("/gestao/motoboy").session(sessaoMotoboy1)).andExpect(status().isOk());

        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/iniciar", rotaId).session(sessaoMotoboy1).with(csrf()))
                .andExpect(status().is3xxRedirection());

        // Reivindicação atômica: motoboy 2 chega tarde demais.
        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/iniciar", rotaId).session(sessaoMotoboy2).with(csrf()))
                .andExpect(status().isConflict());

        TenantContext.set(schema);
        List<EntregaParada> paradas;
        try {
            EntregaRota rota = entregaRotaRepository.findByIdComParadas(rotaId).orElseThrow();
            assertThat(rota.getStatus()).isEqualTo(StatusEntregaRota.EM_EXECUCAO);
            Usuario entregador = usuarioRepository.findById(rota.getEntregador().getId()).orElseThrow();
            assertThat(entregador.getEmail()).isEqualTo(emailMotoboy1);
            paradas = rota.getParadas();
            assertThat(paradas).hasSize(2);
            assertThat(paradas.get(0).getStatus()).isEqualTo(StatusEntregaParada.A_CAMINHO);
            assertThat(paradas.get(1).getStatus()).isEqualTo(StatusEntregaParada.PENDENTE);
        } finally {
            TenantContext.clear();
        }
        Long primeiraParadaId = paradas.get(0).getId();
        Long segundaParadaId = paradas.get(1).getId();

        // Fora de ordem: motoboy não consegue agir na segunda parada antes da primeira.
        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/paradas/{paradaId}/chegada", rotaId, segundaParadaId)
                        .session(sessaoMotoboy1).with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/paradas/{paradaId}/chegada", rotaId, primeiraParadaId)
                        .session(sessaoMotoboy1).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/paradas/{paradaId}/confirmar", rotaId, primeiraParadaId)
                        .session(sessaoMotoboy1).with(csrf())
                        .param("formaPagamentoRecebida", "Dinheiro"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/paradas/{paradaId}/chegada", rotaId, segundaParadaId)
                        .session(sessaoMotoboy1).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/paradas/{paradaId}/confirmar", rotaId, segundaParadaId)
                        .session(sessaoMotoboy1).with(csrf())
                        .param("formaPagamentoRecebida", "Dinheiro"))
                .andExpect(status().is3xxRedirection());

        TenantContext.set(schema);
        try {
            EntregaRota rota = entregaRotaRepository.findByIdComParadas(rotaId).orElseThrow();
            assertThat(rota.getStatus()).isEqualTo(StatusEntregaRota.CONCLUIDA);
            assertThat(rota.getFinalizadaEm()).isNotNull();
            assertThat(rota.getParadas()).allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(StatusEntregaParada.ENTREGUE));
            // Comissão: 80% de (8.00 + 12.00) = 16.00
            assertThat(rota.getPercentualComissaoSnapshot()).isEqualByComparingTo("80");
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(get("/gestao/motoboy/rotas/{id}", rotaId).session(sessaoMotoboy1)).andExpect(status().isOk());

        // Resumo de comissão por motoboy: soma em cima de todas as rotas já assumidas.
        var comissoes = mockMvc.perform(get("/gestao/entregas/comissoes").session(sessaoDono))
                .andExpect(status().isOk())
                .andReturn();
        String corpoComissoes = comissoes.getResponse().getContentAsString();
        assertThat(corpoComissoes).contains(emailMotoboy1);
        assertThat(corpoComissoes).contains("16.00");

        // Admin cancela uma segunda rota: paradas pendentes cancelam junto e as vendas voltam a ficar elegíveis.
        var criacaoRota2 = mockMvc.perform(post("/gestao/entregas").session(sessaoDono).with(csrf())
                        .param("vendaIds", String.valueOf(vendaId3), String.valueOf(vendaId4))
                        .param("origem", "Loja Central, João Pessoa"))
                .andExpect(redirectedUrlPattern("/gestao/entregas/rotas/*"))
                .andReturn();
        String location2 = criacaoRota2.getResponse().getRedirectedUrl();
        Long rota2Id = Long.valueOf(location2.substring(location2.lastIndexOf('/') + 1));

        mockMvc.perform(post("/gestao/entregas/rotas/{id}/cancelar", rota2Id).session(sessaoDono).with(csrf())
                        .param("motivo", "Endereço errado"))
                .andExpect(status().is3xxRedirection());

        TenantContext.set(schema);
        try {
            EntregaRota rota2 = entregaRotaRepository.findByIdComParadas(rota2Id).orElseThrow();
            assertThat(rota2.getStatus()).isEqualTo(StatusEntregaRota.CANCELADA);
            assertThat(rota2.getCancelamentoMotivo()).isEqualTo("Endereço errado");
            assertThat(rota2.getParadas()).allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(StatusEntregaParada.CANCELADA));
        } finally {
            TenantContext.clear();
        }

        var listaAposCancelamento = mockMvc.perform(get("/gestao/entregas").session(sessaoDono))
                .andExpect(status().isOk())
                .andReturn();
        String corpoLista = listaAposCancelamento.getResponse().getContentAsString();
        assertThat(corpoLista).contains("Rua dos Coqueiros, 300, João Pessoa");
        assertThat(corpoLista).contains("Rua do Sol, 400, João Pessoa");
    }

    /**
     * Rastreio público do cliente (via GPS enviado pelo motoboy) e
     * reconciliação de dinheiro coletado (venda paga só na entrega, em
     * espécie — o motoboy fica com a comissão e devolve o resto pra loja).
     */
    @Test
    void motoboyAtualizaLocalizacaoClienteAcompanhaRastreioEDinheiroColetadoReconcilia() throws Exception {
        String sufixo = "entrega-rastreio-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();

        TenantContext.set(schema);
        Long vendaId1;
        Long vendaId2;
        String emailMotoboy = "motoboy-rastreio@" + sufixo + ".example";
        try {
            appSettingService.upsert("entrega.motoboy.comissao_percentual", "80", "Comissão do motoboy");

            Long unidadeId = unidadeMedidaRepository.save(new UnidadeMedida("UN", "Unidade", (short) 0, false)).getId();
            Long localId = localEstoqueRepository.save(new LocalEstoque("Loja", "LOJA", true)).getId();
            Long produtoId = produtoRepository.save(new Produto("Produto entregável",
                    unidadeMedidaRepository.findById(unidadeId).orElseThrow())).getId();

            vendaId1 = criarVendaEmEntregaSemPagamento(localId, produtoId, unidadeId,
                    "Rua das Flores, 100, João Pessoa", new BigDecimal("5.00"));
            vendaId2 = criarVendaEmEntregaSemPagamento(localId, produtoId, unidadeId,
                    "Rua das Palmeiras, 200, João Pessoa", new BigDecimal("5.00"));

            Permissao entregaExecutar = permissaoRepository.findByCodigo("ENTREGA_EXECUTAR").orElseThrow();
            Papel papelMotoboy = papelRepository.save(new Papel("Motoboy", "Executa rotas de entrega", false));
            papelMotoboy.getPermissoes().add(entregaExecutar);
            papelMotoboy = papelRepository.save(papelMotoboy);
            criarUsuarioComPapel(emailMotoboy, "Motoboy Rastreio", papelMotoboy.getId());
        } finally {
            TenantContext.clear();
        }
        criarIdentidade(empresaRepository.findBySchemaNome(schema).orElseThrow(), emailMotoboy);

        MockHttpSession sessaoDono = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(sessaoDono).with(csrf())
                        .param("email", "dono@" + sufixo + ".example")
                        .param("senha", "senhaForte123"))
                .andExpect(status().is3xxRedirection());
        MockHttpSession sessaoMotoboy = login(emailMotoboy, "senhaMotoboy123");

        var criacao = mockMvc.perform(post("/gestao/entregas").session(sessaoDono).with(csrf())
                        .param("vendaIds", String.valueOf(vendaId1), String.valueOf(vendaId2))
                        .param("origem", "Loja Central, João Pessoa"))
                .andExpect(redirectedUrlPattern("/gestao/entregas/rotas/*"))
                .andReturn();
        Long rotaId = Long.valueOf(criacao.getResponse().getRedirectedUrl()
                .substring(criacao.getResponse().getRedirectedUrl().lastIndexOf('/') + 1));

        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/iniciar", rotaId).session(sessaoMotoboy).with(csrf()))
                .andExpect(status().is3xxRedirection());

        List<EntregaParada> paradas;
        TenantContext.set(schema);
        try {
            paradas = entregaRotaRepository.findByIdComParadas(rotaId).orElseThrow().getParadas();
        } finally {
            TenantContext.clear();
        }
        Long primeiraParadaId = paradas.get(0).getId();
        Long segundaParadaId = paradas.get(1).getId();
        String tokenSegundaParada = "" + paradas.get(1).getTokenRastreio();

        // Rastreio antes de qualquer ping de GPS: ainda não dá pra estimar ETA, mas já sabemos a fila.
        var rastreioSemPosicao = mockMvc.perform(get("/rastreio/{token}", tokenSegundaParada)
                        .header("X-Empresa", schema))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(rastreioSemPosicao.getResponse().getContentAsString()).contains("falta");

        // Motoboy envia a posição (ping de GPS da tela de rota).
        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/localizacao", rotaId).session(sessaoMotoboy).with(csrf())
                        .param("latitude", "-7.1195")
                        .param("longitude", "-34.8450"))
                .andExpect(status().isNoContent());

        var rastreioComPosicao = mockMvc.perform(get("/rastreio/{token}", tokenSegundaParada)
                        .header("X-Empresa", schema))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(rastreioComPosicao.getResponse().getContentAsString()).contains("min");

        // Entrega as duas paradas, recebendo em dinheiro nas duas (COD).
        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/paradas/{paradaId}/chegada", rotaId, primeiraParadaId)
                        .session(sessaoMotoboy).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/paradas/{paradaId}/confirmar", rotaId, primeiraParadaId)
                        .session(sessaoMotoboy).with(csrf())
                        .param("formaPagamentoRecebida", "Dinheiro"))
                .andExpect(status().is3xxRedirection());

        // Depois que a primeira parada foi entregue, a fila da segunda parada zera.
        var rastreioProximo = mockMvc.perform(get("/rastreio/{token}", tokenSegundaParada)
                        .header("X-Empresa", schema))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(rastreioProximo.getResponse().getContentAsString()).contains("próxima entrega");

        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/paradas/{paradaId}/chegada", rotaId, segundaParadaId)
                        .session(sessaoMotoboy).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/paradas/{paradaId}/confirmar", rotaId, segundaParadaId)
                        .session(sessaoMotoboy).with(csrf())
                        .param("formaPagamentoRecebida", "Dinheiro"))
                .andExpect(status().is3xxRedirection());

        // Reconciliação: 2 vendas de 50,00 + frete 5,00 cada = 55,00 de total, nunca pagas antes ->
        // motoboy coletou 110,00 em espécie. Comissão: 80% de (5,00 + 5,00) de frete = 8,00. Ele fica
        // com os 8,00 e devolve 102,00 pra loja.
        TenantContext.set(schema);
        try {
            EntregaRotaService.GanhoMotoboyView ganho = entregaRotaService.calcularGanho(rotaId);
            assertThat(ganho.dinheiroColetado()).isEqualByComparingTo("110.00");
            assertThat(ganho.comissaoConfirmada()).isEqualByComparingTo("8.00");
            assertThat(ganho.valorDevolverLoja()).isEqualByComparingTo("102.00");
            assertThat(ganho.comissaoNaoCobertaPorDinheiro()).isEqualByComparingTo("0.00");
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Imprevisto/segurança: motoboy relata durante a rota (não bloqueia
     * nem muda a parada), tipo grave vira alerta ativo pro admin em
     * /gestao/entregas, some do alerta assim que resolvida.
     */
    @Test
    void motoboyRelataOcorrenciaGraveViraAlertaProAdminEResolveSome() throws Exception {
        String sufixo = "entrega-ocorrencia-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();

        TenantContext.set(schema);
        Long vendaId1;
        Long vendaId2;
        String emailMotoboy = "motoboy-ocorrencia@" + sufixo + ".example";
        try {
            Long unidadeId = unidadeMedidaRepository.save(new UnidadeMedida("UN", "Unidade", (short) 0, false)).getId();
            Long localId = localEstoqueRepository.save(new LocalEstoque("Loja", "LOJA", true)).getId();
            Long produtoId = produtoRepository.save(new Produto("Produto entregável",
                    unidadeMedidaRepository.findById(unidadeId).orElseThrow())).getId();
            Long formaPagamentoId = formaPagamentoRepository.save(
                    new FormaPagamento("Dinheiro", NaturezaFormaPagamento.DINHEIRO, true)).getId();

            vendaId1 = criarVendaEmEntrega(localId, produtoId, unidadeId, formaPagamentoId,
                    "Rua das Flores, 100, João Pessoa", new BigDecimal("5.00"));
            vendaId2 = criarVendaEmEntrega(localId, produtoId, unidadeId, formaPagamentoId,
                    "Rua das Palmeiras, 200, João Pessoa", new BigDecimal("5.00"));

            Permissao entregaExecutar = permissaoRepository.findByCodigo("ENTREGA_EXECUTAR").orElseThrow();
            Papel papelMotoboy = papelRepository.save(new Papel("Motoboy", "Executa rotas de entrega", false));
            papelMotoboy.getPermissoes().add(entregaExecutar);
            papelMotoboy = papelRepository.save(papelMotoboy);
            criarUsuarioComPapel(emailMotoboy, "Motoboy Ocorrência", papelMotoboy.getId());
        } finally {
            TenantContext.clear();
        }
        criarIdentidade(empresaRepository.findBySchemaNome(schema).orElseThrow(), emailMotoboy);

        MockHttpSession sessaoDono = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(sessaoDono).with(csrf())
                        .param("email", "dono@" + sufixo + ".example")
                        .param("senha", "senhaForte123"))
                .andExpect(status().is3xxRedirection());
        MockHttpSession sessaoMotoboy = login(emailMotoboy, "senhaMotoboy123");

        var criacao = mockMvc.perform(post("/gestao/entregas").session(sessaoDono).with(csrf())
                        .param("vendaIds", String.valueOf(vendaId1), String.valueOf(vendaId2))
                        .param("origem", "Loja Central, João Pessoa"))
                .andExpect(redirectedUrlPattern("/gestao/entregas/rotas/*"))
                .andReturn();
        Long rotaId = Long.valueOf(criacao.getResponse().getRedirectedUrl()
                .substring(criacao.getResponse().getRedirectedUrl().lastIndexOf('/') + 1));

        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/iniciar", rotaId).session(sessaoMotoboy).with(csrf()))
                .andExpect(status().is3xxRedirection());

        // Ainda sem nenhuma ocorrência: sem alerta.
        var listaAntes = mockMvc.perform(get("/gestao/entregas").session(sessaoDono)).andExpect(status().isOk()).andReturn();
        assertThat(listaAntes.getResponse().getContentAsString()).doesNotContain("Alertas de segurança abertos");

        // Ocorrência leve (não grave): registra, mas não vira alerta.
        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/ocorrencias", rotaId).session(sessaoMotoboy).with(csrf())
                        .param("tipo", "TRANSITO_OU_CONGESTIONAMENTO")
                        .param("descricao", "Avenida bloqueada, indo por outra rua"))
                .andExpect(status().is3xxRedirection());

        var listaSoLeve = mockMvc.perform(get("/gestao/entregas").session(sessaoDono)).andExpect(status().isOk()).andReturn();
        assertThat(listaSoLeve.getResponse().getContentAsString()).doesNotContain("Alertas de segurança abertos");

        // Ocorrência grave: vira alerta ativo.
        mockMvc.perform(post("/gestao/motoboy/rotas/{id}/ocorrencias", rotaId).session(sessaoMotoboy).with(csrf())
                        .param("tipo", "CLIENTE_AGRESSIVO_OU_AMEACA")
                        .param("descricao", "Cliente ameaçou na porta")
                        .param("latitude", "-7.1195")
                        .param("longitude", "-34.8450"))
                .andExpect(status().is3xxRedirection());

        var listaComAlerta = mockMvc.perform(get("/gestao/entregas").session(sessaoDono)).andExpect(status().isOk()).andReturn();
        String corpoComAlerta = listaComAlerta.getResponse().getContentAsString();
        assertThat(corpoComAlerta).contains("Alertas de segurança abertos");
        assertThat(corpoComAlerta).contains("Rota");

        var detalheComOcorrencias = mockMvc.perform(get("/gestao/entregas/rotas/{id}", rotaId).session(sessaoDono))
                .andExpect(status().isOk()).andReturn();
        String corpoDetalhe = detalheComOcorrencias.getResponse().getContentAsString();
        assertThat(corpoDetalhe).contains("Cliente ameaçou na porta");
        assertThat(corpoDetalhe).contains("Grave");

        Long ocorrenciaGraveId;
        TenantContext.set(schema);
        try {
            var ocorrencias = entregaOcorrenciaRepository.findByRotaIdOrderByCriadoEmDesc(rotaId);
            assertThat(ocorrencias).hasSize(2);
            ocorrenciaGraveId = ocorrencias.stream()
                    .filter(o -> o.getTipo() == TipoOcorrencia.CLIENTE_AGRESSIVO_OU_AMEACA)
                    .findFirst().orElseThrow().getId();
        } finally {
            TenantContext.clear();
        }

        // Admin resolve: some do alerta.
        mockMvc.perform(post("/gestao/entregas/rotas/{id}/ocorrencias/{ocorrenciaId}/resolver", rotaId, ocorrenciaGraveId)
                        .session(sessaoDono).with(csrf()))
                .andExpect(status().is3xxRedirection());

        var listaDepoisResolver = mockMvc.perform(get("/gestao/entregas").session(sessaoDono)).andExpect(status().isOk()).andReturn();
        assertThat(listaDepoisResolver.getResponse().getContentAsString()).doesNotContain("Alertas de segurança abertos");

        TenantContext.set(schema);
        try {
            EntregaOcorrencia resolvida = entregaOcorrenciaRepository.findById(ocorrenciaGraveId).orElseThrow();
            assertThat(resolvida.isResolvida()).isTrue();
        } finally {
            TenantContext.clear();
        }
    }

    /** Via RegistrarVendaCommand (não manipulando a entidade direto) — é o caminho real que o checkout usa. */
    private Long criarVendaEmEntrega(Long localId, Long produtoId, Long unidadeId, Long formaPagamentoId,
                                      String endereco, BigDecimal frete) {
        RegistrarVendaCommand cmd = new RegistrarVendaCommand(null, CanalVenda.ONLINE, localId, null, null, null, null,
                BigDecimal.ZERO, frete, ModoEntrega.ENTREGA, endereco,
                List.of(new RegistrarVendaCommand.ItemVendaCommand(produtoId, new BigDecimal("1"), unidadeId,
                        new BigDecimal("50.00"), BigDecimal.ZERO)),
                List.of(new RegistrarVendaCommand.PagamentoVendaCommand(formaPagamentoId, new BigDecimal("58.00"),
                        new BigDecimal("58.00"), BigDecimal.ZERO)));
        return vendaService.registrar(cmd).getId();
    }

    /** COD: nenhum pagamento registrado ainda — o total inteiro fica pra cobrar na entrega. */
    private Long criarVendaEmEntregaSemPagamento(Long localId, Long produtoId, Long unidadeId, String endereco, BigDecimal frete) {
        RegistrarVendaCommand cmd = new RegistrarVendaCommand(null, CanalVenda.ONLINE, localId, null, null, null, null,
                BigDecimal.ZERO, frete, ModoEntrega.ENTREGA, endereco,
                List.of(new RegistrarVendaCommand.ItemVendaCommand(produtoId, new BigDecimal("1"), unidadeId,
                        new BigDecimal("50.00"), BigDecimal.ZERO)),
                List.of());
        return vendaService.registrar(cmd).getId();
    }

    private Long criarUsuarioComPapel(String email, String nome, Long papelId) {
        Papel papel = papelRepository.findById(papelId).orElseThrow();
        Usuario usuario = new Usuario(nome, email);
        usuario.getPapeis().add(papel);
        return usuarioRepository.save(usuario).getId();
    }

    private void criarIdentidade(Empresa empresa, String email) {
        TenantContext.set(schema);
        Long usuarioIdTenant;
        try {
            usuarioIdTenant = usuarioRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        } finally {
            TenantContext.clear();
        }
        identidadeUsuarioRepository.save(new IdentidadeUsuario(email, empresa.getId(), usuarioIdTenant,
                PASSWORD_ENCODER.encode("senhaMotoboy123")));
    }

    private MockHttpSession login(String email, String senha) throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(sessao).with(csrf())
                        .param("email", email)
                        .param("senha", senha))
                .andExpect(status().is3xxRedirection());
        return sessao;
    }
}
