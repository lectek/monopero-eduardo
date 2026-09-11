package br.com.lojagenerica.gestao.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lojagenerica.core.cadastro.CondicaoPagamento;
import br.com.lojagenerica.core.cadastro.CondicaoPagamentoRepository;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
import java.math.BigDecimal;
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
class CondicaoPagamentoGestaoFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;
    @Autowired
    private CondicaoPagamentoRepository condicaoPagamentoRepository;

    private String schema;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void loginCriarComEntradaPercentualEEditar() throws Exception {
        String sufixo = "gestao-cp-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(session).with(csrf())
                        .param("email", "dono@" + sufixo + ".example")
                        .param("senha", "senhaForte123"))
                .andExpect(redirectedUrl("/gestao"));

        mockMvc.perform(post("/gestao/condicoes-pagamento").session(session).with(csrf())
                        .param("nome", "30/60/90")
                        .param("parcelas", "3")
                        .param("intervaloDias", "30")
                        .param("entradaPercentual", "10.5"))
                .andExpect(redirectedUrl("/gestao/condicoes-pagamento"));

        mockMvc.perform(get("/gestao/condicoes-pagamento").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("30/60/90")));

        TenantContext.set(schema);
        Long id;
        try {
            List<CondicaoPagamento> condicoes = condicaoPagamentoRepository.findAll();
            assertThat(condicoes).hasSize(1);
            CondicaoPagamento condicao = condicoes.get(0);
            assertThat(condicao.getParcelas()).isEqualTo((short) 3);
            assertThat(condicao.getIntervaloDias()).isEqualTo(30);
            assertThat(condicao.getEntradaPercentual()).isEqualByComparingTo(new BigDecimal("10.5"));
            id = condicao.getId();
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(post("/gestao/condicoes-pagamento/" + id).session(session).with(csrf())
                        .param("nome", "30/60/90 revisado")
                        .param("parcelas", "3")
                        .param("intervaloDias", "45")
                        .param("ativo", "true"))
                .andExpect(redirectedUrl("/gestao/condicoes-pagamento"));

        TenantContext.set(schema);
        try {
            CondicaoPagamento atualizada = condicaoPagamentoRepository.findById(id).orElseThrow();
            assertThat(atualizada.getNome()).isEqualTo("30/60/90 revisado");
            assertThat(atualizada.getIntervaloDias()).isEqualTo(45);
        } finally {
            TenantContext.clear();
        }
    }
}
