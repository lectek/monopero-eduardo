package br.com.lojagenerica.multitenancy;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * Fail-closed: nenhum repositório do módulo {@code core.*} pode rodar sem um
 * tenant resolvido. Isso pega o caso "esqueci de proteger essa rota" antes
 * que ele vire uma query sem contexto (que, sem isto, cairia por padrão no
 * schema "plataforma" — silenciosamente errado, não um erro óbvio).
 */
@Aspect
@Component
public class TenantGuard {

    @Pointcut("execution(* br.com.lojagenerica.core..*Repository+.*(..))")
    void chamadaRepositorioDeNucleo() {
    }

    @Before("chamadaRepositorioDeNucleo()")
    public void exigirTenantResolvido() {
        TenantContext.require();
    }
}
