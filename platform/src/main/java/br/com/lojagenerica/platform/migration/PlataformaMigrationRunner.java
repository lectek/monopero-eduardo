package br.com.lojagenerica.platform.migration;

import br.com.lojagenerica.multitenancy.TenantContext;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Autoconfigure padrão do Spring Boot só conhece 1 schema — este runner
 * substitui isso, rodando o Flyway manualmente contra "plataforma" antes de
 * qualquer outra coisa (@Order 0). {@link TenantMigrationRunner} roda depois
 * (@Order 1), uma vez por schema de tenant.
 */
@Component
@Order(0)
public class PlataformaMigrationRunner implements ApplicationRunner {

    private final DataSource dataSource;

    public PlataformaMigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(TenantContext.PLATAFORMA)
                .defaultSchema(TenantContext.PLATAFORMA)
                .locations("classpath:db/migration/plataforma")
                .load()
                .migrate();
    }
}
