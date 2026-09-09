package br.com.lojagenerica.multitenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Diz ao Hibernate qual "tenant identifier" (= nome do schema, aqui) usar
 * pra sessão atual. {@link SchemaMultiTenantConnectionProvider} usa esse
 * mesmo valor pra fazer o {@code SET search_path}.
 */
@Component
public class SchemaTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.isSet() ? TenantContext.get() : TenantContext.PLATAFORMA;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
