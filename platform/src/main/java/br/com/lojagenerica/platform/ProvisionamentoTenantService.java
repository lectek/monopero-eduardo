package br.com.lojagenerica.platform;

import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.domain.Empresa;
import br.com.lojagenerica.platform.domain.EmpresaRepository;
import br.com.lojagenerica.platform.domain.IdentidadeUsuario;
import br.com.lojagenerica.platform.domain.IdentidadeUsuarioRepository;
import br.com.lojagenerica.platform.domain.StatusEmpresa;
import br.com.lojagenerica.platform.migration.TenantBootstrapService;
import br.com.lojagenerica.platform.migration.TenantMigrationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Provisiona um tenant novo — tudo ou nada: qualquer falha no meio do
 * caminho derruba (DROP SCHEMA) o que já tiver sido criado.
 *
 * <p>Deliberadamente NÃO é {@code @Transactional}: os passos tocam dois
 * schemas diferentes (plataforma, depois o schema novo, depois plataforma
 * de novo pra IdentidadeUsuario) e cada schema precisa de uma sessão
 * Hibernate própria, resolvida com o {@link TenantContext} certo no momento
 * em que ela é aberta — uma única transação ambiente reaproveitaria a
 * mesma sessão (e o mesmo schema resolvido) para tudo, o que é exatamente
 * o bug que se quer evitar (ver TenantBootstrapService).
 */
@Service
public class ProvisionamentoTenantService {

    private static final Logger log = LoggerFactory.getLogger(ProvisionamentoTenantService.class);

    private final EmpresaRepository empresaRepository;
    private final IdentidadeUsuarioRepository identidadeUsuarioRepository;
    private final TenantMigrationRunner tenantMigrationRunner;
    private final TenantBootstrapService tenantBootstrapService;
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public ProvisionamentoTenantService(
            EmpresaRepository empresaRepository,
            IdentidadeUsuarioRepository identidadeUsuarioRepository,
            TenantMigrationRunner tenantMigrationRunner,
            TenantBootstrapService tenantBootstrapService,
            JdbcTemplate jdbcTemplate) {
        this.empresaRepository = empresaRepository;
        this.identidadeUsuarioRepository = identidadeUsuarioRepository;
        this.tenantMigrationRunner = tenantMigrationRunner;
        this.tenantBootstrapService = tenantBootstrapService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Empresa provisionar(ProvisionarEmpresaCommand cmd) {
        if (empresaRepository.findBySubdominio(cmd.subdominio()).isPresent()) {
            throw new ProvisionamentoException("Subdomínio já em uso: " + cmd.subdominio());
        }

        String schema = gerarNomeSchema();
        Empresa empresa = empresaRepository.save(
                new Empresa(schema, cmd.razaoSocial(), cmd.nomeFantasia(), cmd.documento(), cmd.subdominio()));

        try {
            tenantMigrationRunner.migrateOne(schema);

            Long usuarioIdTenant;
            TenantContext.set(schema);
            try {
                usuarioIdTenant = tenantBootstrapService.criarAdministradorInicial(
                        cmd.administradorNome(), cmd.administradorEmail());
            } finally {
                TenantContext.clear();
            }

            identidadeUsuarioRepository.save(new IdentidadeUsuario(
                    cmd.administradorEmail(), empresa.getId(), usuarioIdTenant,
                    passwordEncoder.encode(cmd.administradorSenha())));

            empresa.marcarAtiva();
            return empresaRepository.save(empresa);
        } catch (RuntimeException ex) {
            log.error("Provisionamento de '{}' falhou, revertendo schema {}: {}",
                    cmd.razaoSocial(), schema, ex.getMessage(), ex);
            droparSchemaSeExistir(schema);
            empresa.marcarFalha(StatusEmpresa.PROVISIONAMENTO_FALHOU);
            empresaRepository.save(empresa);
            throw new ProvisionamentoException("Falha provisionando empresa: " + ex.getMessage(), ex);
        }
    }

    private String gerarNomeSchema() {
        Long numero = jdbcTemplate.queryForObject("select nextval('plataforma.empresa_schema_seq')", Long.class);
        return "empresa_%03d".formatted(numero);
    }

    private void droparSchemaSeExistir(String schema) {
        try {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
        } catch (RuntimeException dropEx) {
            log.error("Falha ao reverter (DROP SCHEMA) {}: {}", schema, dropEx.getMessage(), dropEx);
        }
    }
}
