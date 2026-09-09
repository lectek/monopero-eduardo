package br.com.lojagenerica.platform;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lojagenerica.core.acesso.Papel;
import br.com.lojagenerica.core.acesso.PapelRepository;
import br.com.lojagenerica.core.acesso.PermissaoCatalogo;
import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.core.cadastro.TipoMovimentacaoRepository;
import br.com.lojagenerica.core.cadastro.TipoMovimentacaoSistema;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.domain.Empresa;
import br.com.lojagenerica.platform.domain.EmpresaRepository;
import br.com.lojagenerica.platform.domain.IdentidadeUsuarioRepository;
import br.com.lojagenerica.platform.domain.StatusEmpresa;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ProvisionamentoTenantServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProvisionamentoTenantService provisionamentoTenantService;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private IdentidadeUsuarioRepository identidadeUsuarioRepository;
    @Autowired
    private PapelRepository papelRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private TipoMovimentacaoRepository tipoMovimentacaoRepository;

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    @Test
    void provisionaEmpresaComSchemaIsoladoEAdminFuncional() {
        Empresa empresa = provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                "Empresa Um LTDA", "Empresa Um", "12345678000190", "empresa-um-" + System.nanoTime(),
                "Dono", "dono@empresa-um.example", "senhaForte123"));

        assertThat(empresa.getStatus()).isEqualTo(StatusEmpresa.ATIVA);
        assertThat(empresa.getSchemaNome()).matches("empresa_\\d+");

        assertThat(identidadeUsuarioRepository.findByEmailIgnoreCaseAndAtivoTrue("dono@empresa-um.example"))
                .hasSize(1);

        TenantContext.set(empresa.getSchemaNome());
        try {
            Optional<Papel> administrador = papelRepository.findByNomeComPermissoes("ADMINISTRADOR");
            assertThat(administrador).isPresent();
            assertThat(administrador.get().isSistema()).isTrue();
            assertThat(administrador.get().getPermissoes())
                    .hasSize(PermissaoCatalogo.values().length);

            assertThat(usuarioRepository.findByEmailIgnoreCase("dono@empresa-um.example")).isPresent();

            for (TipoMovimentacaoSistema seed : TipoMovimentacaoSistema.values()) {
                assertThat(tipoMovimentacaoRepository.findByCodigo(seed.codigo())).isPresent();
            }
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void subdominioDuplicadoFalhaSemCriarSegundoSchema() {
        String subdominio = "duplicado-" + System.nanoTime();
        provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                "Primeira", "Primeira", null, subdominio, "Dono", "dono1@ex.example", "senhaForte123"));

        long totalAntes = empresaRepository.count();

        org.junit.jupiter.api.Assertions.assertThrows(ProvisionamentoException.class, () ->
                provisionamentoTenantService.provisionar(new ProvisionarEmpresaCommand(
                        "Segunda", "Segunda", null, subdominio, "Dono2", "dono2@ex.example", "outraSenha123")));

        assertThat(empresaRepository.count()).isEqualTo(totalAntes);
    }
}
