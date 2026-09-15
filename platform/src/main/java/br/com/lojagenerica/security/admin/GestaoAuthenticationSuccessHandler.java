package br.com.lojagenerica.security.admin;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/**
 * Sem isto, {@code defaultSuccessUrl("/gestao", true)} mandava TODO mundo
 * pra tela genérica de Cadastros depois do login — inclusive um motoboy,
 * que não tem permissão pra quase nada ali e precisava notar sozinho o
 * link "Minhas rotas" no meio do menu. {@code alwaysUseDefaultTargetUrl}
 * também descartava qualquer link direto que tivesse motivado o redirect
 * pro login (ex.: alguém abriu {@code /gestao/motoboy/rotas/5} sem estar
 * logado) — aqui a saved request é respeitada primeiro, e só na ausência
 * dela é que o destino é escolhido por papel.
 */
public class GestaoAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved != null) {
            response.sendRedirect(saved.getRedirectUrl());
            return;
        }
        response.sendRedirect(request.getContextPath() + destinoPadrao(authentication));
    }

    /**
     * Motoboy "puro" (só executa entrega, sem nenhuma permissão de
     * cadastro) cai direto na própria tela de rotas — administrador tem
     * {@code ENTREGA_EXECUTAR} também (papel de sistema recebe todo o
     * catálogo de permissões), mas cai em {@code /gestao} normalmente por
     * também ter {@code CADASTRO_GERENCIAR}.
     */
    private String destinoPadrao(Authentication authentication) {
        boolean executaEntrega = false;
        boolean gerenciaCadastro = false;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ENTREGA_EXECUTAR".equals(authority.getAuthority())) {
                executaEntrega = true;
            }
            if ("CADASTRO_GERENCIAR".equals(authority.getAuthority())) {
                gerenciaCadastro = true;
            }
        }
        return (executaEntrega && !gerenciaCadastro) ? "/gestao/motoboy" : "/gestao";
    }
}
