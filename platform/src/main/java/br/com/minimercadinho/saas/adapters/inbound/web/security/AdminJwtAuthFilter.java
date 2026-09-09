package br.com.minimercadinho.saas.adapters.inbound.web.security;

import br.com.minimercadinho.saas.adapters.outbound.auth.service.AuthTokenOperations;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gate simples pra /api/admin/** e /api/motoboy/**: exige "Authorization: Bearer
 * <token>" válido (emitido pelo /api/auth/login) antes de deixar a requisição
 * passar. Usa o mesmo AuthTokenOperations (JWT portado do ParaisoPet) que emite
 * o token — admin_users cobre os dois papéis (role="ADMIN" ou "MOTOBOY").
 *
 * Autorização por role: token sem "ADMIN" não passa em /api/admin/**, token sem
 * "MOTOBOY" não passa em /api/motoboy/** — sem isso, uma conta de motoboy teria
 * acesso total ao painel admin só por ter um token válido (era o risco descrito
 * como "autorização fina por role, se precisar, vem depois" — chegou a hora).
 *
 * Não é uma SecurityFilterChain completa (sem Spring Security nesta etapa) — só
 * valida o token e expõe email/role como atributos de request.
 */
@Component
public class AdminJwtAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_EMAIL = "admin.email";
    public static final String ATTR_ROLES = "admin.roles";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MOTOBOY = "MOTOBOY";

    private final AuthTokenOperations authTokenOperations;

    public AdminJwtAuthFilter(AuthTokenOperations authTokenOperations) {
        this.authTokenOperations = authTokenOperations;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/admin/") && !uri.startsWith("/api/motoboy/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token ausente.");
            return;
        }
        Object roles;
        try {
            Map<String, Object> claims = authTokenOperations.validateAccessToken(header);
            roles = claims.get("roles");
            request.setAttribute(ATTR_EMAIL, claims.get("username"));
            request.setAttribute(ATTR_ROLES, roles);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inválido ou expirado.");
            return;
        }

        String uri = request.getRequestURI();
        String requiredRole = uri.startsWith("/api/motoboy/") ? ROLE_MOTOBOY : ROLE_ADMIN;
        if (!hasRole(roles, requiredRole)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Sem permissão para este recurso.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasRole(Object rolesClaim, String role) {
        if (rolesClaim instanceof Collection<?> roles) {
            return roles.stream().anyMatch(r -> role.equals(String.valueOf(r)));
        }
        return rolesClaim != null && role.equals(String.valueOf(rolesClaim));
    }
}
