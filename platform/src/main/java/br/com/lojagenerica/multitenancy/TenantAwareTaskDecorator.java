package br.com.lojagenerica.multitenancy;

import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * Propaga o {@link TenantContext} pra threads de {@code @Async}/executores —
 * sem isso, trabalho assíncrono disparado dentro de uma request resolve pro
 * schema "plataforma" (o default), não pro tenant da request que o disparou.
 */
public class TenantAwareTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        String schema = TenantContext.isSet() ? TenantContext.get() : null;
        return () -> {
            if (schema != null) {
                TenantContext.set(schema);
            }
            try {
                runnable.run();
            } finally {
                TenantContext.clear();
            }
        };
    }
}
