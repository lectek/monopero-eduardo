package br.com.lojagenerica.gestao.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.cadastro.LocalEstoqueRepository;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
import java.util.List;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class LocalEstoqueGestaoFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;
    @Autowired
    private LocalEstoqueRepository localEstoqueRepository;

    private String schema;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void loginCriarComoPrincipalEDesativar() throws Exception {
        String sufixo = "gestao-le-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(session).with(csrf())
                        .param("email", "dono@" + sufixo + ".example")
                        .param("senha", "senhaForte123"))
                .andExpect(redirectedUrl("/gestao"));

        mockMvc.perform(post("/gestao/locais-estoque").session(session).with(csrf())
                        .param("nome", "Loja Principal")
                        .param("tipo", "LOJA")
                        .param("principal", "true"))
                .andExpect(redirectedUrl("/gestao/locais-estoque"));

        mockMvc.perform(get("/gestao/locais-estoque").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Loja Principal")));

        TenantContext.set(schema);
        Long id;
        try {
            List<LocalEstoque> locais = localEstoqueRepository.findAll();
            assertThat(locais).hasSize(1);
            assertThat(locais.get(0).isPrincipal()).isTrue();
            id = locais.get(0).getId();
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(post("/gestao/locais-estoque/" + id + "/alternar-ativo").session(session).with(csrf())
                        .param("ativo", "false"))
                .andExpect(redirectedUrl("/gestao/locais-estoque"));

        TenantContext.set(schema);
        try {
            assertThat(localEstoqueRepository.findById(id).orElseThrow().isAtivo()).isFalse();
        } finally {
            TenantContext.clear();
        }
    }
}
