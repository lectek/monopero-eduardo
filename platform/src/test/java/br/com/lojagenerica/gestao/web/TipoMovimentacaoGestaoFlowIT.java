package br.com.lojagenerica.gestao.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lojagenerica.core.cadastro.TipoMovimentacao;
import br.com.lojagenerica.core.cadastro.TipoMovimentacaoRepository;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Cobre especificamente a invariante de {@code sistema=true}: linhas
 * seedadas no provisionamento (VENDA, COMPRA, ...) não podem ser
 * editadas nem desativadas por esta tela, mesmo tentando direto pela URL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class TipoMovimentacaoGestaoFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;
    @Autowired
    private TipoMovimentacaoRepository tipoMovimentacaoRepository;

    private String schema;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void linhaDeSistemaNaoPodeSerEditadaNemDesativadaMasLivreSim() throws Exception {
        String sufixo = "gestao-tm-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(session).with(csrf())
                        .param("email", "dono@" + sufixo + ".example")
                        .param("senha", "senhaForte123"))
                .andExpect(redirectedUrl("/gestao"));

        mockMvc.perform(get("/gestao/tipos-movimentacao").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("VENDA")));

        TenantContext.set(schema);
        Long vendaId;
        try {
            vendaId = tipoMovimentacaoRepository.findByCodigo("VENDA").orElseThrow().getId();
        } finally {
            TenantContext.clear();
        }

        // GET editar numa linha de sistema não mostra o form — redireciona de volta.
        mockMvc.perform(get("/gestao/tipos-movimentacao/" + vendaId + "/editar").session(session))
                .andExpect(redirectedUrl("/gestao/tipos-movimentacao"));

        // Tentar desativar direto pela URL também não faz nada.
        mockMvc.perform(post("/gestao/tipos-movimentacao/" + vendaId + "/alternar-ativo").session(session).with(csrf())
                        .param("ativo", "false"))
                .andExpect(redirectedUrl("/gestao/tipos-movimentacao"));

        TenantContext.set(schema);
        try {
            assertThat(tipoMovimentacaoRepository.findById(vendaId).orElseThrow().isAtivo()).isTrue();
        } finally {
            TenantContext.clear();
        }

        // Linha livre do usuário: cria, edita e desativa normalmente.
        mockMvc.perform(post("/gestao/tipos-movimentacao").session(session).with(csrf())
                        .param("codigo", "PERDA")
                        .param("nome", "Perda")
                        .param("sentido", "SAIDA")
                        .param("exigeMotivo", "true"))
                .andExpect(redirectedUrl("/gestao/tipos-movimentacao"));

        TenantContext.set(schema);
        Long perdaId;
        try {
            TipoMovimentacao perda = tipoMovimentacaoRepository.findByCodigo("PERDA").orElseThrow();
            assertThat(perda.isSistema()).isFalse();
            assertThat(perda.isExigeMotivo()).isTrue();
            perdaId = perda.getId();
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(post("/gestao/tipos-movimentacao/" + perdaId + "/alternar-ativo").session(session).with(csrf())
                        .param("ativo", "false"))
                .andExpect(redirectedUrl("/gestao/tipos-movimentacao"));

        TenantContext.set(schema);
        try {
            assertThat(tipoMovimentacaoRepository.findById(perdaId).orElseThrow().isAtivo()).isFalse();
        } finally {
            TenantContext.clear();
        }
    }
}
