package br.com.lojagenerica.security;

import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.domain.Empresa;
import br.com.lojagenerica.platform.domain.EmpresaRepository;
import br.com.lojagenerica.platform.domain.StatusEmpresa;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gate de {@code /api/public/**} e {@code /webhooks/**} (storefront e
 * checkout — sem JWT, ninguém logado ainda). {@link JwtAuthenticationFilter}
 * só resolve tenant pra {@code /api/v1/**}; sem este filtro, uma request
 * pública rodava com {@link TenantContext} nunca setado, e
 * {@code TenantGuard} falhava (fail-closed) na primeira chamada de
 * repositório do módulo core — foi assim que este gap foi descoberto (ver
 * CheckoutServiceIT).
 *
 * <p>Resolução por subdomínio primeiro (produção: {@code empresa.dominio.com}
 * -> {@code empresa.subdominio} no schema "plataforma"); no dev/teste, sem
 * subdomínio de verdade disponível, cai pro header {@code X-Empresa} com o
 * nome do schema — mesmo fallback previsto no design original pra
 * ferramentas/dev, nunca aceito sozinho em produção (só complementa quando o
 * Host não tem subdomínio).
 */
@Component
public class PublicTenantResolutionFilter extends OncePerRequestFilter {

    private static final String HEADER_EMPRESA = "X-Empresa";

    private final EmpresaRepository empresaRepository;

    public PublicTenantResolutionFilter(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.startsWith("/api/public/") || uri.startsWith("/webhooks/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<Empresa> empresa = resolverPorSubdominio(request).or(() -> resolverPorHeader(request));

        if (empresa.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Não foi possível identificar a empresa da requisição (subdomínio ou header " + HEADER_EMPRESA + ").");
            return;
        }
        if (empresa.get().getStatus() != StatusEmpresa.ATIVA) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Empresa inativa.");
            return;
        }

        TenantContext.set(empresa.get().getSchemaNome());
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Optional<Empresa> resolverPorSubdominio(HttpServletRequest request) {
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        String hostname = host.split(":")[0];
        String[] partes = hostname.split("\\.");
        if (partes.length < 3) {
            return Optional.empty(); // "localhost", "empresa.com" — sem subdomínio de verdade
        }
        return empresaRepository.findBySubdominio(partes[0]);
    }

    private Optional<Empresa> resolverPorHeader(HttpServletRequest request) {
        String schema = request.getHeader(HEADER_EMPRESA);
        if (schema == null || schema.isBlank()) {
            return Optional.empty();
        }
        return empresaRepository.findBySchemaNome(schema);
    }
}
