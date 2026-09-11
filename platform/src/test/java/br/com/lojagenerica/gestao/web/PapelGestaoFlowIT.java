package br.com.lojagenerica.gestao.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lojagenerica.core.acesso.Papel;
import br.com.lojagenerica.core.acesso.PapelRepository;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class PapelGestaoFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;
    @Autowired
    private PapelRepository papelRepository;

    private String schema;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void administradorNaoEEditavelEPapelLivreCriaEEdita() throws Exception {
        String sufixo = "gestao-papel-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(session).with(csrf())
                        .param("email", "dono@" + sufixo + ".example")
                        .param("senha", "senhaForte123"))
                .andExpect(redirectedUrl("/gestao"));

        mockMvc.perform(get("/gestao/papeis").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("ADMINISTRADOR")));

        TenantContext.set(schema);
        Long administradorId;
        try {
            administradorId = papelRepository.findByNome("ADMINISTRADOR").orElseThrow().getId();
        } finally {
            TenantContext.clear();
        }

        // ADMINISTRADOR não pode ser editado por esta tela.
        mockMvc.perform(get("/gestao/papeis/" + administradorId + "/editar").session(session))
                .andExpect(redirectedUrl("/gestao/papeis"));

        mockMvc.perform(post("/gestao/papeis").session(session).with(csrf())
                        .param("nome", "Vendedor")
                        .param("descricao", "Acesso ao PDV e vendas")
                        .param("permissoes", "VENDA_LER", "VENDA_CRIAR"))
                .andExpect(redirectedUrl("/gestao/papeis"));

        TenantContext.set(schema);
        Long vendedorId;
        try {
            Papel vendedor = papelRepository.findByNomeComPermissoes("Vendedor").orElseThrow();
            assertThat(vendedor.isSistema()).isFalse();
            assertThat(vendedor.getPermissoes()).hasSize(2);
            vendedorId = vendedor.getId();
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(post("/gestao/papeis/" + vendedorId).session(session).with(csrf())
                        .param("nome", "Vendedor")
                        .param("descricao", "Acesso ao PDV, vendas e cancelamento")
                        .param("permissoes", "VENDA_LER", "VENDA_CRIAR", "VENDA_CANCELAR"))
                .andExpect(redirectedUrl("/gestao/papeis"));

        TenantContext.set(schema);
        try {
            Papel vendedor = papelRepository.findByIdComPermissoes(vendedorId).orElseThrow();
            assertThat(vendedor.getPermissoes()).hasSize(3);
        } finally {
            TenantContext.clear();
        }
    }
}
