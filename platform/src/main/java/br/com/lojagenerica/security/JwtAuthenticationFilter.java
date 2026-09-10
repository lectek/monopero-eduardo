package br.com.lojagenerica.security;

import br.com.lojagenerica.adapters.outbound.auth.service.AuthTokenOperations;
import br.com.lojagenerica.core.auditoria.AuditoriaContext;
import br.com.lojagenerica.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gate de {@code /api/v1/**} (exceto {@code /api/v1/auth/**}, público): exige
 * um JWT válido, resolve o {@link TenantContext} a partir do claim
 * "tenantId" (o schema — autoritativo, emitido só no login,
 * ver AuthController) e popula uma {@link Authentication} de verdade no
 * {@link SecurityContextHolder} — uma authority por código de permissão,
 * habilitando {@code @PreAuthorize("hasAuthority('X')")} nos controllers
 * novos.
 *
 * <p>Não reescreve {@code AdminJwtAuthFilter} (que continua protegendo
 * {@code /api/admin/**}/{@code /api/motoboy/**} do jeito antigo, sem tenant)
 * — os dois coexistem até a Fase C repontar checkout/entrega pro modelo
 * novo (ver docs/ROADMAP.md).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthTokenOperations authTokenOperations;

    public JwtAuthenticationFilter(AuthTokenOperations authTokenOperations) {
        this.authTokenOperations = authTokenOperations;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // /api/v1/pdv/sync/** é autenticado por TerminalAuthenticationFilter
        // (chave de API do terminal, não JWT de usuário — o caixa sincroniza
        // sem ninguém logado).
        return !uri.startsWith("/api/v1/") || uri.startsWith("/api/v1/auth/") || uri.startsWith("/api/v1/pdv/sync/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token ausente.");
            return;
        }

        Map<String, Object> claims;
        try {
            claims = authTokenOperations.validateAccessToken(header);
        } catch (RuntimeException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inválido ou expirado.");
            return;
        }

        Object tenantClaim = claims.get("tenantId");
        if (!(tenantClaim instanceof String tenantId) || tenantId.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token sem empresa associada.");
            return;
        }

        String username = String.valueOf(claims.getOrDefault("username", claims.getOrDefault("sub", "")));
        List<SimpleGrantedAuthority> authorities = extrairAuthorities(claims.get("roles"));

        TenantContext.set(tenantId);
        AuditoriaContext.set(null, username, request.getRemoteAddr(), request.getHeader("User-Agent"));
        try {
            Authentication authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
            AuditoriaContext.clear();
        }
    }

    private List<SimpleGrantedAuthority> extrairAuthorities(Object rolesClaim) {
        if (rolesClaim instanceof Collection<?> roles) {
            return roles.stream().map(r -> new SimpleGrantedAuthority(String.valueOf(r))).toList();
        }
        return List.of();
    }
}
