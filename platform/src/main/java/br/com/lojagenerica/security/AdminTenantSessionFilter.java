package br.com.lojagenerica.security;

import br.com.lojagenerica.core.auditoria.AuditoriaContext;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.security.admin.AdminPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gate de {@code /gestao/**}: diferente de {@link JwtAuthenticationFilter}
 * (stateless, um JWT por requisição), aqui a {@link Authentication} já foi
 * restaurada da sessão HTTP pelo próprio Spring Security antes deste filtro
 * rodar — só falta religar o {@link TenantContext} a partir do schema
 * guardado no {@link AdminPrincipal} (a sessão não sabe nada de tenant por
 * si só, então isso tem que ser refeito em toda requisição, não só no
 * login).
 */
@Component
public class AdminTenantSessionFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/gestao/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof AdminPrincipal principal)) {
            // Página de login (ainda anônimo) ou sessão expirada — o form login do Spring Security
            // já cuida do redirecionamento; aqui só não há schema pra religar.
            filterChain.doFilter(request, response);
            return;
        }

        TenantContext.set(principal.schema());
        AuditoriaContext.set(principal.usuarioId(), principal.email(), request.getRemoteAddr(), request.getHeader("User-Agent"));
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            AuditoriaContext.clear();
        }
    }
}
