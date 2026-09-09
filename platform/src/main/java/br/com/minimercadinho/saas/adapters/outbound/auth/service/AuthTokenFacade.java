package br.com.minimercadinho.saas.adapters.outbound.auth.service;

import br.com.minimercadinho.saas.adapters.outbound.auth.config.JwtProperties;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.model.RefreshToken;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.model.TokenPair;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.model.TokenSubject;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.service.JwtTokenService;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.store.RefreshTokenStore;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.store.TokenBlacklist;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix="jwt", name={"enabled"}, havingValue="true", matchIfMissing=false)
public class AuthTokenFacade
implements AuthTokenOperations {
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenBlacklist tokenBlacklist;
    private final JwtProperties props;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthTokenFacade(JwtTokenService jwtTokenService, RefreshTokenStore refreshTokenStore, TokenBlacklist tokenBlacklist, JwtProperties props, Clock clock) {
        this.jwtTokenService = Objects.requireNonNull(jwtTokenService);
        this.refreshTokenStore = Objects.requireNonNull(refreshTokenStore);
        this.tokenBlacklist = Objects.requireNonNull(tokenBlacklist);
        this.props = Objects.requireNonNull(props);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional
    public TokenPair issueTokens(Long userId, String username, String tenantId, List<String> roles, String userAgent, String ip) {
        Instant now = this.clock.instant();
        Instant accessExp = now.plus((long)this.props.getAccessTokenExpirationMinutes().intValue(), ChronoUnit.MINUTES);
        Instant refreshExp = now.plus((long)this.props.getRefreshTokenExpirationMinutes().intValue(), ChronoUnit.MINUTES);
        TokenSubject subject = new TokenSubject(userId, username, tenantId, roles);
        Map<String, Object> claims = Map.of("tenantId", tenantId, "roles", roles == null ? List.of() : roles, "username", username == null ? "" : username, "typ", "access");
        String accessToken = this.jwtTokenService.generateAccessToken(subject, claims);
        String refreshOpaque = this.generateOpaqueToken();
        RefreshToken refresh = RefreshToken.newToken(userId, tenantId, refreshOpaque, now, refreshExp, userAgent, ip);
        this.refreshTokenStore.save(refresh);
        return new TokenPair(accessToken, refreshOpaque, now, accessExp);
    }

    @Override
    @Transactional
    public TokenPair refreshUsing(String refreshTokenValue, String userAgent, String ip) {
        Instant now = this.clock.instant();
        if (this.tokenBlacklist.isBlacklisted(refreshTokenValue)) {
            throw new IllegalStateException("Refresh token em blacklist");
        }
        RefreshToken stored = this.refreshTokenStore.findValidByToken(refreshTokenValue).orElseThrow(() -> new IllegalStateException("Refresh token n\u00e3o encontrado/expirado/revogado"));
        this.refreshTokenStore.revokeByToken(refreshTokenValue, now);
        this.tokenBlacklist.blacklist(refreshTokenValue, stored.getExpiresAt());
        return this.issueTokens(stored.getUserId(), null, stored.getTenantId(), List.of(), userAgent, ip);
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String refreshTokenValue) {
        RefreshToken stored = this.refreshTokenStore.findValidByToken(refreshTokenValue).orElseThrow(() -> new IllegalStateException("Refresh token n\u00e3o encontrado"));
        Instant now = this.clock.instant();
        this.refreshTokenStore.revokeByToken(refreshTokenValue, now);
        this.tokenBlacklist.blacklist(refreshTokenValue, stored.getExpiresAt());
    }

    @Override
    @Transactional
    public void revokeAllForUser(Long userId, String tenantId) {
        this.refreshTokenStore.revokeAllForUser(userId, tenantId, this.clock.instant());
    }

    @Override
    public Map<String, Object> validateAccessToken(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            throw new IllegalArgumentException("access token vazio");
        }
        String raw = AuthTokenFacade.stripPrefix(jwt, this.props.getPrefix());
        if (this.tokenBlacklist.isBlacklisted(raw)) {
            throw new IllegalStateException("Access token em blacklist");
        }
        // Bug herdado do ParaisoPet: aqui passava "jwt" (ainda com o prefixo "Bearer ")
        // pro parser, que sempre rejeitava o token. O parser espera só o JWT puro.
        return this.jwtTokenService.parseAndValidate(raw);
    }

    @Override
    public Map<String, Object> parseAccessToken(String jwt) {
        return this.validateAccessToken(jwt);
    }

    @Override
    @Transactional
    public void revokeAccessToken(String accessToken) {
        Map<String, Object> claims = this.jwtTokenService.parseAndValidate(accessToken);
        Object expClaim = claims.get("exp");
        Instant exp = expClaim instanceof Number ? Instant.ofEpochSecond(((Number)expClaim).longValue()) : this.clock.instant().plus((long)this.props.getAccessTokenExpirationMinutes().intValue(), ChronoUnit.MINUTES);
        this.tokenBlacklist.blacklist(AuthTokenFacade.stripPrefix(accessToken, this.props.getPrefix()), exp);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        this.secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String stripPrefix(String token, String prefix) {
        if (token == null || prefix == null || prefix.isBlank()) {
            return token;
        }
        String p = prefix + " ";
        return token.startsWith(p) ? token.substring(p.length()) : token;
    }
}

