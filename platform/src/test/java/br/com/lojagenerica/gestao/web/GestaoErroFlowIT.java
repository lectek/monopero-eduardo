package br.com.lojagenerica.gestao.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
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
 * Prova que {@link GestaoExceptionHandler} realmente intercepta antes do
 * {@code RestExceptionTranslator} global — sem ele, cada um destes três
 * casos devolvia JSON cru com HTTP 500 pro navegador (ver docs/ROADMAP.md,
 * "aprimorar e refinar").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class GestaoErroFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    private MockHttpSession logar(String sufixo) throws Exception {
        provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(session).with(csrf())
                        .param("email", "dono@" + sufixo + ".example")
                        .param("senha", "senhaForte123"))
                .andExpect(redirectedUrl("/gestao"));
        return session;
    }

    @Test
    void editarIdInexistenteMostraPaginaAmigavelNao500Cru() throws Exception {
        MockHttpSession session = logar("gestao-erro-404-" + System.nanoTime());

        mockMvc.perform(get("/gestao/produtos/999999/editar").session(session))
                .andExpect(status().isNotFound())
                .andExpect(view().name("pages/gestao/erro"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("\"status\""))));
    }

    @Test
    void nomeDuplicadoMostraPaginaAmigavelNao500Cru() throws Exception {
        MockHttpSession session = logar("gestao-erro-dup-" + System.nanoTime());

        mockMvc.perform(post("/gestao/marcas").session(session).with(csrf())
                        .param("nome", "Tramontina"))
                .andExpect(redirectedUrl("/gestao/marcas"));

        mockMvc.perform(post("/gestao/marcas").session(session).with(csrf())
                        .param("nome", "Tramontina"))
                .andExpect(status().isConflict())
                .andExpect(view().name("pages/gestao/erro"))
                .andExpect(content().string(Matchers.containsString("Já existe um registro")));
    }

    @Test
    void nomeEmBrancoMostraPaginaAmigavelNao500Cru() throws Exception {
        MockHttpSession session = logar("gestao-erro-vazio-" + System.nanoTime());

        mockMvc.perform(post("/gestao/marcas").session(session).with(csrf())
                        .param("nome", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("pages/gestao/erro"));
    }
}
