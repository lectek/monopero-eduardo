package br.com.lojagenerica.platform.migration;

import br.com.lojagenerica.platform.domain.Empresa;
import br.com.lojagenerica.platform.domain.EmpresaRepository;
import br.com.lojagenerica.platform.domain.StatusEmpresa;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Aplica {@code db/migration/tenant} num schema específico —
 * {@link #migrateOne(String)} é chamado tanto no boot (uma vez por empresa
 * já existente, @Order 1) quanto pelo provisionamento de uma empresa nova.
 * Falha de UMA empresa não derruba as demais nem a plataforma inteira.
 */
@Component
@Order(1)
public class TenantMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationRunner.class);

    private final DataSource dataSource;
    private final EmpresaRepository empresaRepository;

    public TenantMigrationRunner(DataSource dataSource, EmpresaRepository empresaRepository) {
        this.dataSource = dataSource;
        this.empresaRepository = empresaRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Empresa empresa : empresaRepository.findAll()) {
            if (empresa.getStatus() == StatusEmpresa.PROVISIONAMENTO_FALHOU) {
                continue; // schema provavelmente nem existe (drop no rollback do provisionamento)
            }
            try {
                migrateOne(empresa.getSchemaNome());
            } catch (RuntimeException ex) {
                log.error("Falha migrando schema do tenant {} ({}): {}",
                        empresa.getSchemaNome(), empresa.getNomeFantasia(), ex.getMessage(), ex);
                empresa.marcarFalha(StatusEmpresa.MIGRACAO_FALHOU);
                empresaRepository.save(empresa);
            }
        }
    }

    public void migrateOne(String schema) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration/tenant")
                .load()
                .migrate();
    }
}
