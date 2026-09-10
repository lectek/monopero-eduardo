package br.com.lojagenerica.security;

import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.pdv.Terminal;
import br.com.lojagenerica.pdv.TerminalRepository;
import br.com.lojagenerica.pdv.TerminalService;
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
 * Gate de {@code /api/v1/pdv/sync/**} — o terminal não tem usuário logado,
 * então nem {@link JwtAuthenticationFilter} nem
 * {@link PublicTenantResolutionFilter} servem aqui (que por sua vez pula
 * este prefixo — ver {@link JwtAuthenticationFilter#shouldNotFilter}).
 * A chave de API embute o schema do tenant como prefixo (ver
 * {@link TerminalService}), resolvida ANTES de existir qualquer contexto,
 * exatamente como o login de usuário resolve pelo schema "plataforma"
 * antes de saber o tenant.
 */
@Component
public class TerminalAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATTR_TERMINAL_ID = "pdv.terminalId";
    private static final String HEADER_API_KEY = "X-Terminal-Api-Key";

    private final EmpresaRepository empresaRepository;
    private final TerminalRepository terminalRepository;

    public TerminalAuthenticationFilter(EmpresaRepository empresaRepository, TerminalRepository terminalRepository) {
        this.empresaRepository = empresaRepository;
        this.terminalRepository = terminalRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/pdv/sync/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER_API_KEY);
        String schema = apiKey == null ? null : TerminalService.resolverSchemaDaChave(apiKey);
        if (schema == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Chave de API do terminal ausente ou inválida.");
            return;
        }

        Optional<Empresa> empresa = empresaRepository.findBySchemaNome(schema);
        if (empresa.isEmpty() || empresa.get().getStatus() != StatusEmpresa.ATIVA) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Chave de API do terminal inválida.");
            return;
        }

        TenantContext.set(schema);
        try {
            String hash = TerminalService.sha256Hex(apiKey);
            Terminal terminal = terminalRepository.findByApiKeyHash(hash).orElse(null);
            if (terminal == null || !terminal.isAtivo()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Chave de API do terminal inválida ou desativada.");
                return;
            }
            request.setAttribute(ATTR_TERMINAL_ID, terminal.getId());
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
