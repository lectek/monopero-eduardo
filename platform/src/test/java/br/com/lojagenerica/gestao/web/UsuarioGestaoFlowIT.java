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
import br.com.lojagenerica.core.acesso.Usuario;
import br.com.lojagenerica.core.acesso.UsuarioRepository;
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
 * O teste mais importante desta leva: prova que criar um usuário pela
 * tela de fato produz um login funcional — não só uma linha na tabela
 * tenant, mas o par completo (usuario + plataforma.identidade_usuario)
 * que UsuarioGestaoService escreve atravessando os dois schemas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class UsuarioGestaoFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PapelRepository papelRepository;

    private String schema;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void criarUsuarioNovoLogaComEleDesativarBloqueiaLoginERedefinirSenhaFunciona() throws Exception {
        String sufixo = "gestao-usr-" + System.nanoTime();
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                sufixo, sufixo, null, sufixo, "Dono", "dono@" + sufixo + ".example", "senhaForte123"));
        schema = empresa.getSchemaNome();

        MockHttpSession sessaoDono = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(sessaoDono).with(csrf())
                        .param("email", "dono@" + sufixo + ".example")
                        .param("senha", "senhaForte123"))
                .andExpect(redirectedUrl("/gestao"));

        TenantContext.set(schema);
        Long administradorId;
        try {
            administradorId = papelRepository.findByNome("ADMINISTRADOR").orElseThrow().getId();
        } finally {
            TenantContext.clear();
        }

        String emailFuncionario = "vendedor@" + sufixo + ".example";
        mockMvc.perform(post("/gestao/usuarios").session(sessaoDono).with(csrf())
                        .param("nome", "Funcionário Teste")
                        .param("email", emailFuncionario)
                        .param("senha", "outraSenhaForte456")
                        .param("papelIds", String.valueOf(administradorId)))
                .andExpect(redirectedUrl("/gestao/usuarios"));

        mockMvc.perform(get("/gestao/usuarios").session(sessaoDono))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Funcionário Teste")));

        // O usuário novo consegue logar de verdade, numa sessão própria — prova que
        // identidade_usuario foi criado com o hash certo, atravessando os dois schemas.
        MockHttpSession sessaoFuncionario = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(sessaoFuncionario).with(csrf())
                        .param("email", emailFuncionario)
                        .param("senha", "outraSenhaForte456"))
                .andExpect(redirectedUrl("/gestao"));

        TenantContext.set(schema);
        Long funcionarioId;
        try {
            Usuario funcionario = usuarioRepository.findAllComPapeis().stream()
                    .filter(u -> u.getEmail().equals(emailFuncionario)).findFirst().orElseThrow();
            assertThat(funcionario.getPapeis()).extracting(Papel::getId).containsExactly(administradorId);
            funcionarioId = funcionario.getId();
        } finally {
            TenantContext.clear();
        }

        // Desativa o usuário — login deve parar de funcionar.
        mockMvc.perform(post("/gestao/usuarios/" + funcionarioId).session(sessaoDono).with(csrf())
                        .param("nome", "Funcionário Teste")
                        .param("papelIds", String.valueOf(administradorId))
                        .param("ativo", "false"))
                .andExpect(redirectedUrl("/gestao/usuarios"));

        MockHttpSession tentativaAposDesativar = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(tentativaAposDesativar).with(csrf())
                        .param("email", emailFuncionario)
                        .param("senha", "outraSenhaForte456"))
                .andExpect(redirectedUrl("/gestao/login?erro"));

        // Reativa e redefine a senha — login com a senha antiga falha, com a nova funciona.
        mockMvc.perform(post("/gestao/usuarios/" + funcionarioId).session(sessaoDono).with(csrf())
                        .param("nome", "Funcionário Teste")
                        .param("papelIds", String.valueOf(administradorId))
                        .param("ativo", "true"))
                .andExpect(redirectedUrl("/gestao/usuarios"));

        mockMvc.perform(post("/gestao/usuarios/" + funcionarioId + "/redefinir-senha").session(sessaoDono).with(csrf())
                        .param("novaSenha", "senhaNovaDeVerdade789"))
                .andExpect(redirectedUrl("/gestao/usuarios/" + funcionarioId + "/editar"));

        MockHttpSession senhaAntiga = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(senhaAntiga).with(csrf())
                        .param("email", emailFuncionario)
                        .param("senha", "outraSenhaForte456"))
                .andExpect(redirectedUrl("/gestao/login?erro"));

        MockHttpSession senhaNova = new MockHttpSession();
        mockMvc.perform(post("/gestao/login").session(senhaNova).with(csrf())
                        .param("email", emailFuncionario)
                        .param("senha", "senhaNovaDeVerdade789"))
                .andExpect(redirectedUrl("/gestao"));
    }
}
