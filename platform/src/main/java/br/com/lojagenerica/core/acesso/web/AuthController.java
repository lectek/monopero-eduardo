package br.com.lojagenerica.core.acesso.web;

import br.com.lojagenerica.adapters.outbound.auth.jwt.model.TokenPair;
import br.com.lojagenerica.adapters.outbound.auth.service.AuthTokenOperations;
import br.com.lojagenerica.core.acesso.Papel;
import br.com.lojagenerica.core.acesso.Permissao;
import br.com.lojagenerica.core.acesso.Usuario;
import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.multitenancy.TenantContext;
import br.com.lojagenerica.platform.IdentidadeService;
import br.com.lojagenerica.platform.ResultadoAutenticacao;
import br.com.lojagenerica.platform.domain.Empresa;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login novo, ciente de multiempresa — substitui gradualmente
 * {@code AdminAuthController} (que ainda emite token com
 * {@code TENANT_UNICO} fixo; os dois coexistem até a Fase C repontar
 * checkout/entrega pro modelo novo, ver docs/ROADMAP.md).
 *
 * <p>Fluxo: resolve a(s) empresa(s) do e-mail no schema "plataforma"
 * (nenhum tenant resolvido ainda) → escolhe uma (ou pede pro cliente
 * escolher, se ambíguo) → só ENTÃO seta {@link TenantContext} e carrega
 * usuário/papéis/permissões do schema daquela empresa → emite JWT com o
 * schema como claim "tenantId" e os códigos de permissão como "roles"
 * (reaproveitando o claim existente — ver AuthTokenOperations).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final IdentidadeService identidadeService;
    private final UsuarioRepository usuarioRepository;
    private final AuthTokenOperations authTokenOperations;

    public AuthController(IdentidadeService identidadeService, UsuarioRepository usuarioRepository,
                           AuthTokenOperations authTokenOperations) {
        this.identidadeService = identidadeService;
        this.usuarioRepository = usuarioRepository;
        this.authTokenOperations = authTokenOperations;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        ResultadoAutenticacao resultado = identidadeService.autenticar(
                request.email(), request.senha(), request.empresaId());

        return switch (resultado) {
            case ResultadoAutenticacao.Falha ignored ->
                    ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErroResponse("E-mail ou senha inválidos."));

            case ResultadoAutenticacao.PrecisaEscolherEmpresa escolha ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(new EscolhaEmpresaResponse(
                            escolha.empresas().stream().map(EmpresaResumo::from).toList()));

            case ResultadoAutenticacao.Sucesso sucesso -> autenticarNoTenant(sucesso, httpRequest);
        };
    }

    private ResponseEntity<?> autenticarNoTenant(ResultadoAutenticacao.Sucesso sucesso, HttpServletRequest httpRequest) {
        Empresa empresa = sucesso.empresa();
        Usuario usuario;
        List<String> permissoes;

        TenantContext.set(empresa.getSchemaNome());
        try {
            usuario = usuarioRepository.findByIdComPapeisEPermissoes(sucesso.usuarioIdTenant())
                    .orElseThrow(() -> new IllegalStateException(
                            "usuario_id_tenant " + sucesso.usuarioIdTenant() + " não existe no schema " + empresa.getSchemaNome()));
            if (!usuario.isAtivo()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErroResponse("Usuário inativo."));
            }
            permissoes = usuario.getPapeis().stream()
                    .flatMap(p -> p.getPermissoes().stream())
                    .map(Permissao::getCodigo)
                    .distinct()
                    .collect(Collectors.toList());

            usuario.registrarAcesso();
            usuarioRepository.save(usuario);
        } finally {
            TenantContext.clear();
        }

        TokenPair tokens = authTokenOperations.issueTokens(
                usuario.getId(), usuario.getEmail(), empresa.getSchemaNome(), permissoes,
                httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr());

        List<String> nomesPapeis = usuario.getPapeis().stream().map(Papel::getNome).toList();
        return ResponseEntity.ok(new LoginResponse(tokens.getAccessToken(), tokens.getRefreshToken(),
                tokens.getExpiresAt(), empresa.getId(), empresa.getNomeFantasia(), nomesPapeis, permissoes));
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String senha, Long empresaId) {
    }

    public record LoginResponse(String accessToken, String refreshToken, Instant expiresAt,
                                 Long empresaId, String empresaNome, List<String> papeis, List<String> permissoes) {
    }

    public record EscolhaEmpresaResponse(List<EmpresaResumo> empresas) {
    }

    public record EmpresaResumo(Long id, String nomeFantasia) {
        static EmpresaResumo from(Empresa empresa) {
            return new EmpresaResumo(empresa.getId(), empresa.getNomeFantasia());
        }
    }

    public record ErroResponse(String mensagem) {
    }
}
