package br.com.lojagenerica.multitenancy;

import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Liga o schema-por-tenant no Hibernate — sem isso, {@link SchemaTenantIdentifierResolver}
 * e {@link SchemaMultiTenantConnectionProvider} existem mas nunca são usados.
 */
@Component
public class MultiTenancyHibernateCustomizer implements HibernatePropertiesCustomizer {

    private final SchemaMultiTenantConnectionProvider connectionProvider;
    private final SchemaTenantIdentifierResolver identifierResolver;

    public MultiTenancyHibernateCustomizer(
            SchemaMultiTenantConnectionProvider connectionProvider,
            SchemaTenantIdentifierResolver identifierResolver) {
        this.connectionProvider = connectionProvider;
        this.identifierResolver = identifierResolver;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, identifierResolver);
    }
}
