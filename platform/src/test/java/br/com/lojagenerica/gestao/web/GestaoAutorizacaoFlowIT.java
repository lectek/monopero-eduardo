package br.com.lojagenerica.gestao.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lojagenerica.core.acesso.Papel;
import br.com.lojagenerica.core.acesso.PapelRepository;
import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.ProvisionamentoTenantService;
import br.com.lojagenerica.platform.ProvisionarEmpresaCommand;
import br.com.lojagenerica.platform.domain.Empresa;
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
 * Até esta leva, todos os testes de {@code /gestao/**} logavam como o dono
 * (ADMINISTRADOR, todas as permissões) — {@code @PreAuthorize} nunca foi
 * provado NEGANDO acesso de verdade. Este teste cria um papel estreito
 * (só {@code PRODUTO_LER}), um usuário com esse papel, e confirma que ele
 * lê produtos mas não escreve, e não entra em outro cadastro qualquer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class GestaoAutorizacaoFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;
    @Autowired
    private PapelRepository papelRepository;
    @Autowired
    private UnidadeMedidaRepository unidadeMedidaRepository;

    private String schema;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void usuarioComPapelEstreitoLeMasNaoEscreveENaoEntraEmOutroCadastro() throws Exception {
        String sufixo = "gestao-autz-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();

        MockHttpSession sessaoDono = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(sessaoDono).with(csrf())
                        .param("email", "dono@" + sufixo + ".example")
                        .param("senha", "senhaForte123"))
                .andExpect(redirectedUrl("/gestao"));

        // Papel estreito: só PRODUTO_LER.
        mockMvc.perform(post("/gestao/papeis").session(sessaoDono).with(csrf())
                        .param("nome", "Leitor de produtos")
                        .param("permissoes", "PRODUTO_LER"))
                .andExpect(redirectedUrl("/gestao/papeis"));

        TenantContext.set(schema);
        Long papelLeitorId;
        Long unidadeId;
        try {
            Papel leitor = papelRepository.findByNomeComPermissoes("Leitor de produtos").orElseThrow();
            assertThat(leitor.getPermissoes()).hasSize(1);
            papelLeitorId = leitor.getId();
            unidadeId = unidadeMedidaRepository.save(new UnidadeMedida("UN", "Unidade", (short) 0, false)).getId();
        } finally {
            TenantContext.clear();
        }

        String emailLeitor = "leitor@" + sufixo + ".example";
        mockMvc.perform(post("/gestao/usuarios").session(sessaoDono).with(csrf())
                        .param("nome", "Usuário Leitor")
                        .param("email", emailLeitor)
                        .param("senha", "senhaDoLeitor123")
                        .param("papelIds", String.valueOf(papelLeitorId)))
                .andExpect(redirectedUrl("/gestao/usuarios"));

        MockHttpSession sessaoLeitor = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(sessaoLeitor).with(csrf())
                        .param("email", emailLeitor)
                        .param("senha", "senhaDoLeitor123"))
                .andExpect(redirectedUrl("/gestao"));

        // Lê produtos: permitido (tem PRODUTO_LER).
        mockMvc.perform(get("/gestao/produtos").session(sessaoLeitor))
                .andExpect(status().isOk());

        // Tenta criar produto: negado (não tem PRODUTO_ESCREVER) — nem o form de criação abre.
        mockMvc.perform(get("/gestao/produtos/novo").session(sessaoLeitor))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/gestao/produtos").session(sessaoLeitor).with(csrf())
                        .param("nome", "Produto que não devia existir")
                        .param("unidadeEstoqueId", String.valueOf(unidadeId)))
                .andExpect(status().isForbidden());

        // Nem entra em outro cadastro qualquer (CADASTRO_GERENCIAR, que ele também não tem).
        mockMvc.perform(get("/gestao/marcas").session(sessaoLeitor))
                .andExpect(status().isForbidden());
    }
}
