package br.com.lojagenerica.gestao.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lojagenerica.core.cadastro.FormaPagamento;
import br.com.lojagenerica.core.cadastro.FormaPagamentoRepository;
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
class FormaPagamentoGestaoFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;
    @Autowired
    private FormaPagamentoRepository formaPagamentoRepository;

    private String schema;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void loginCriarComParcelamentoEEditar() throws Exception {
        String sufixo = "gestao-fp-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(session).with(csrf())
                        .param("email", "dono@" + sufixo + ".example")
                        .param("senha", "senhaForte123"))
                .andExpect(redirectedUrl("/gestao"));

        mockMvc.perform(post("/gestao/formas-pagamento").session(session).with(csrf())
                        .param("nome", "Cartão de crédito")
                        .param("natureza", "CARTAO_CREDITO")
                        .param("afetaCaixa", "false")
                        .param("permiteParcelamento", "true")
                        .param("maxParcelas", "6")
                        .param("taxaPercentual", "2.5"))
                .andExpect(redirectedUrl("/gestao/formas-pagamento"));

        mockMvc.perform(get("/gestao/formas-pagamento").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Cartão de crédito")));

        TenantContext.set(schema);
        Long id;
        try {
            List<FormaPagamento> formas = formaPagamentoRepository.findAll();
            assertThat(formas).hasSize(1);
            FormaPagamento forma = formas.get(0);
            assertThat(forma.getNatureza().name()).isEqualTo("CARTAO_CREDITO");
            assertThat(forma.isAfetaCaixa()).isFalse();
            assertThat(forma.isPermiteParcelamento()).isTrue();
            assertThat(forma.getMaxParcelas()).isEqualTo((short) 6);
            id = forma.getId();
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(post("/gestao/formas-pagamento/" + id).session(session).with(csrf())
                        .param("nome", "Cartão de crédito Visa/Master")
                        .param("natureza", "CARTAO_CREDITO")
                        .param("afetaCaixa", "false")
                        .param("permiteParcelamento", "true")
                        .param("maxParcelas", "12")
                        .param("ativo", "true"))
                .andExpect(redirectedUrl("/gestao/formas-pagamento"));

        TenantContext.set(schema);
        try {
            FormaPagamento atualizada = formaPagamentoRepository.findById(id).orElseThrow();
            assertThat(atualizada.getNome()).isEqualTo("Cartão de crédito Visa/Master");
            assertThat(atualizada.getMaxParcelas()).isEqualTo((short) 12);
        } finally {
            TenantContext.clear();
        }
    }
}
