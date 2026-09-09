package br.com.minimercadinho.saas.adapters.outbound.auth.jwt.provider;

import br.com.minimercadinho.saas.adapters.outbound.auth.config.JwtProperties;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.exception.TokenGenerationException;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.exception.TokenValidationException;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.model.JwtPrincipal;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.model.RefreshToken;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.model.TokenPair;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.store.RefreshTokenStore;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.store.TokenBlacklist;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class JwtService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TokenProvider tokenProvider;
    private final JwtProperties props;
    private final RefreshTokenStore refreshStore;
    private final TokenBlacklist blacklist;

    public JwtService(TokenProvider tokenProvider, JwtProperties props,
                      RefreshTokenStore refreshStore, TokenBlacklist blacklist) {
        this.tokenProvider = Objects.requireNonNull(tokenProvider);
        this.props = Objects.requireNonNull(props);
        this.refreshStore = Objects.requireNonNull(refreshStore);
        this.blacklist = Objects.requireNonNull(blacklist);
    }

    public TokenPair issueTokens(Long userId, String username, Collection<String> roles,
                                 String tenantId, Map<String, Object> extras) {
        try {
            Instant now = Instant.now();
            Instant accessExp = now.plus(props.getAccessTokenExpirationMinutes(), ChronoUnit.MINUTES);
            Instant refreshExp = now.plus(props.getRefreshTokenExpirationMinutes(), ChronoUnit.MINUTES);

            String jti = UUID.randomUUID().toString();

            Map<String, Object> claims = new HashMap<>();
            claims.put("uid", userId);
            claims.put("tenant", tenantId);
            claims.put("roles", roles == null ? List.of() : List.copyOf(roles));
            if (extras != null && !extras.isEmpty()) {
                claims.put("meta", new HashMap<>(extras));
            }

            String access = tokenProvider.generateAccessToken(username, claims, accessExp);

            Map<String, Object> refreshClaims = new HashMap<>(claims);
            refreshClaims.put("typ", "refresh");
            refreshClaims.put("jti", jti);

            String refresh = tokenProvider.generateRefreshToken(username, jti, refreshClaims, refreshExp);

            RefreshToken rt = RefreshToken.newToken(
                    userId, tenantId, refresh, now, refreshExp,
                    extras != null ? (String) extras.get("userAgent") : null,
                    extras != null ? (String) extras.get("ip") : null
            );
            refreshStore.save(rt);

            return new TokenPair(access, refresh, now, accessExp);
        } catch (RuntimeException ex) {
            throw new TokenGenerationException("Erro ao emitir tokens", ex);
        }
    }

    public JwtPrincipal validateAccessToken(String rawToken) {
        String token = stripPrefix(rawToken);
        if (blacklist.isBlacklisted(token)) {
            throw new TokenValidationException("Token revogado");
        }
        JwtPrincipal principal = tokenProvider.validate(token);
        if (props.getIssuer() != null && !props.getIssuer().equals(principal.getIssuer())) {
            throw new TokenValidationException("Issuer inválido");
        }
        if (props.getAudience() != null &&
            principal.getAudience() != null &&
            !principal.getAudience().contains(props.getAudience())) {
            throw new TokenValidationException("Audience inválida");
        }
        return principal;
    }

    public TokenPair rotateRefreshToken(String rawRefreshToken) {
        String token = stripPrefix(rawRefreshToken);
        if (blacklist.isBlacklisted(token)) {
            throw new TokenValidationException("Refresh token revogado");
        }
        JwtPrincipal principal = tokenProvider.validate(token);

        boolean valid = refreshStore.findValidByToken(token).isPresent();
        if (!valid) {
            throw new TokenValidationException("Refresh expirado/inválido");
        }

        refreshStore.revokeByToken(token, Instant.now());

        Instant exp = principal.getExpiresAt() != null
                ? principal.getExpiresAt()
                : Instant.now().plus(15, ChronoUnit.MINUTES);
        blacklist.blacklist(token, exp);

        return issueTokens(
                principal.getUserId(),
                principal.getSubject(),
                principal.getRoles(),
                principal.getTenant(),
                Map.of("rotated", true)
        );
    }

    public void revokeAccessToken(String rawAccessToken, Instant expiresAt) {
        String token = stripPrefix(rawAccessToken);
        blacklist.blacklist(token, expiresAt != null ? expiresAt : Instant.now().plus(1, ChronoUnit.HOURS));
    }

    public void revokeAllRefreshForUser(Long userId, String tenantId) {
        refreshStore.revokeAllForUser(userId, tenantId, Instant.now());
    }

    private String stripPrefix(String token) {
        if (token == null) return null;
        String prefix = props.getPrefix() + " ";
        return token.startsWith(prefix) ? token.substring(prefix.length()) : token;
    }

    /** Lê JSON para Map<String,Object> usando Jackson. */
    public Map<String, Object> parse(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON inválido", e);
        }
    }
}
