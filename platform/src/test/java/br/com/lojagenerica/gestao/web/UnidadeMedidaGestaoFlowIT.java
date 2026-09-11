package br.com.lojagenerica.gestao.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
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
class UnidadeMedidaGestaoFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;
    @Autowired
    private UnidadeMedidaRepository unidadeMedidaRepository;

    private String schema;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void loginCriarEDesativarUnidadeDeMedida() throws Exception {
        String sufixo = "gestao-um-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(session).with(csrf())
                        .param("email", "dono@" + sufixo + ".example")
                        .param("senha", "senhaForte123"))
                .andExpect(redirectedUrl("/gestao"));

        mockMvc.perform(post("/gestao/unidades-medida").session(session).with(csrf())
                        .param("codigo", "KG")
                        .param("descricao", "Quilograma")
                        .param("casasDecimais", "3")
                        .param("fracionavel", "true"))
                .andExpect(redirectedUrl("/gestao/unidades-medida"));

        mockMvc.perform(get("/gestao/unidades-medida").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("KG")));

        TenantContext.set(schema);
        Long id;
        try {
            List<UnidadeMedida> unidades = unidadeMedidaRepository.findAll();
            assertThat(unidades).hasSize(1);
            assertThat(unidades.get(0).getCasasDecimais()).isEqualTo((short) 3);
            assertThat(unidades.get(0).isFracionavel()).isTrue();
            id = unidades.get(0).getId();
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(post("/gestao/unidades-medida/" + id + "/alternar-ativo").session(session).with(csrf())
                        .param("ativo", "false"))
                .andExpect(redirectedUrl("/gestao/unidades-medida"));

        TenantContext.set(schema);
        try {
            assertThat(unidadeMedidaRepository.findById(id).orElseThrow().isAtivo()).isFalse();
        } finally {
            TenantContext.clear();
        }
    }
}
