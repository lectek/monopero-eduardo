package br.com.lojagenerica.core.acesso;

import br.com.lojagenerica.platform.domain.TenantIterator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Roda depois das migrations (ver @Order nos runners de platform.migration). */
@Component
@Order(2)
public class PermissaoSyncRunner implements ApplicationRunner {

    private final TenantIterator tenantIterator;
    private final PermissaoCatalogSyncService permissaoCatalogSyncService;

    public PermissaoSyncRunner(TenantIterator tenantIterator, PermissaoCatalogSyncService permissaoCatalogSyncService) {
        this.tenantIterator = tenantIterator;
        this.permissaoCatalogSyncService = permissaoCatalogSyncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        tenantIterator.forEachTenant(schema -> permissaoCatalogSyncService.sincronizarSchemaAtual());
    }
}
