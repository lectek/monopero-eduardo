package br.com.lojagenerica.platform.domain;

import br.com.lojagenerica.multitenancy.TenantContext;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job agendado que precisa tocar dado de tenant (não o control plane) tem
 * que passar por aqui — sem isso, um {@code @Scheduled} roda só contra
 * "plataforma" (o schema default quando nenhum tenant está resolvido).
 */
@Component
public class TenantIterator {

    private static final Logger log = LoggerFactory.getLogger(TenantIterator.class);

    private final EmpresaRepository empresaRepository;

    public TenantIterator(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    /**
     * Roda {@code action} uma vez por empresa ATIVA. Falha numa empresa é
     * logada e não interrompe as demais — um job de manutenção não pode
     * travar pra todo mundo por causa de 1 tenant com problema.
     */
    public void forEachTenant(Consumer<String> action) {
        for (Empresa empresa : empresaRepository.findByStatus(StatusEmpresa.ATIVA)) {
            String schema = empresa.getSchemaNome();
            try {
                TenantContext.set(schema);
                action.accept(schema);
            } catch (RuntimeException ex) {
                log.error("Falha executando job de manutenção no tenant {}: {}", schema, ex.getMessage(), ex);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
