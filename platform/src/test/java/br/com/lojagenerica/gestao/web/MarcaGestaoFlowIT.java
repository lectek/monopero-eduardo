package br.com.lojagenerica.gestao.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lojagenerica.core.cadastro.Marca;
import br.com.lojagenerica.core.cadastro.MarcaRepository;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prova a fatia nova de UI web (Fase D, item 2 do roadmap): login de
 * sessão em /gestao/**, resolução de tenant a partir do
 * AdminPrincipal (não JWT), e um CRUD completo de um cadastro (Marca) —
 * o padrão de referência pros outros 5 cadastros construídos do mesmo
 * jeito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class MarcaGestaoFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;
    @Autowired
    private MarcaRepository marcaRepository;

    private String schema;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void semSessaoRedirecionaParaLogin() throws Exception {
        mockMvc.perform(get("/gestao/marcas"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void loginListarCriarEditarEDesativarMarca() throws Exception {
        String sufixo = "gestao-marca-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(session).with(csrf())
                        .param("email", "dono@" + sufixo + ".example")
                        .param("senha", "senhaForte123"))
                .andExpect(redirectedUrl("/gestao"));

        mockMvc.perform(get("/gestao/marcas").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nenhuma marca cadastrada")));

        mockMvc.perform(post("/gestao/marcas").session(session).with(csrf())
                        .param("nome", "Tramontina")
                        .param("fabricante", "Tramontina S.A."))
                .andExpect(redirectedUrl("/gestao/marcas"));

        MvcResult listaComItem = mockMvc.perform(get("/gestao/marcas").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tramontina")))
                .andReturn();
        assertThat(listaComItem.getResponse().getContentAsString()).contains("Ativa");

        TenantContext.set(schema);
        Long marcaId;
        try {
            List<Marca> marcas = marcaRepository.findAll();
            assertThat(marcas).hasSize(1);
            marcaId = marcas.get(0).getId();
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(get("/gestao/marcas/" + marcaId + "/editar").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Editar marca")));

        mockMvc.perform(post("/gestao/marcas/" + marcaId).session(session).with(csrf())
                        .param("nome", "Tramontina Profissional")
                        .param("fabricante", "Tramontina S.A.")
                        .param("ativo", "true"))
                .andExpect(redirectedUrl("/gestao/marcas"));

        mockMvc.perform(post("/gestao/marcas/" + marcaId + "/alternar-ativo").session(session).with(csrf())
                        .param("ativo", "false"))
                .andExpect(redirectedUrl("/gestao/marcas"));

        TenantContext.set(schema);
        try {
            Marca marca = marcaRepository.findById(marcaId).orElseThrow();
            assertThat(marca.getNome()).isEqualTo("Tramontina Profissional");
            assertThat(marca.isAtivo()).isFalse();
        } finally {
            TenantContext.clear();
        }
    }
}
