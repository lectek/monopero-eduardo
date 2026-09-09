package br.com.lojagenerica.adapters.inbound.web.controller;

import br.com.lojagenerica.adapters.outbound.auth.jwt.model.TokenPair;
import br.com.lojagenerica.adapters.outbound.auth.service.AuthTokenOperations;
import br.com.lojagenerica.adapters.outbound.persistence.entity.AdminUserEntity;
import br.com.lojagenerica.adapters.outbound.persistence.jpa.AdminUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login do admin do SaaS — mesmo mecanismo de token (JwtService/AuthTokenFacade,
 * portado do ParaisoPet) que "os outros SaaS" já usam, agora sobre {@code admin_users}.
 */
@RestController
public class AdminAuthController {

    private static final String TENANT_UNICO = "minimercadinho";

    private final AdminUserRepository adminUserRepository;
    private final AuthTokenOperations authTokenOperations;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminAuthController(AdminUserRepository adminUserRepository, AuthTokenOperations authTokenOperations) {
        this.adminUserRepository = adminUserRepository;
        this.authTokenOperations = authTokenOperations;
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        Optional<AdminUserEntity> maybeAdmin = adminUserRepository.findByEmailIgnoreCase(request.email() == null ? "" : request.email().trim());
        boolean credenciaisValidas = maybeAdmin.isPresent()
                && maybeAdmin.get().isAtivo()
                && passwordEncoder.matches(request.senha() == null ? "" : request.senha(), maybeAdmin.get().getSenhaHash());

        if (!credenciaisValidas) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErroResponse("Email ou senha inválidos."));
        }

        AdminUserEntity admin = maybeAdmin.get();
        TokenPair tokens = authTokenOperations.issueTokens(
                admin.getId(),
                admin.getEmail(),
                TENANT_UNICO,
                List.of(admin.getRole()),
                httpRequest.getHeader("User-Agent"),
                httpRequest.getRemoteAddr()
        );
        return ResponseEntity.ok(LoginResponse.from(tokens));
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String senha) {
    }

    public record LoginResponse(String accessToken, String refreshToken, Instant expiresAt) {
        static LoginResponse from(TokenPair tokens) {
            return new LoginResponse(tokens.getAccessToken(), tokens.getRefreshToken(), tokens.getExpiresAt());
        }
    }

    public record ErroResponse(String mensagem) {
    }
}
