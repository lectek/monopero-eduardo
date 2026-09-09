package br.com.minimercadinho.saas.adapters.outbound.auth.jwt.provider;

import br.com.minimercadinho.saas.adapters.outbound.auth.config.JwtProperties;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.model.JwtPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class DefaultTokenProvider implements TokenProvider {

    private final JwtProperties props;
    private final SecretKey key;

    public DefaultTokenProvider(JwtProperties props) {
        this.props = Objects.requireNonNull(props);
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generateAccessToken(String subject,
                                      Map<String, Object> claims,
                                      Instant expiresAt) {

        Instant now = Instant.now();

        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now));

        if (claims != null && !claims.isEmpty()) {
            builder.claims(claims);
        }
        if (props.getIssuer() != null && !props.getIssuer().isBlank()) {
            builder.issuer(props.getIssuer());
        }
        if (expiresAt != null) {
            builder.expiration(Date.from(expiresAt));
        }

        return builder
                .signWith(key) // HSxxx conforme tamanho da chave
                .compact();
    }

    @Override
    public String generateRefreshToken(String subject,
                                       String jti,
                                       Map<String, Object> claims,
                                       Instant expiresAt) {

        Instant now = Instant.now();

        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now));

        if (jti != null && !jti.isBlank()) {
            builder.id(jti);
        }
        if (claims != null && !claims.isEmpty()) {
            builder.claims(claims);
        }
        if (props.getIssuer() != null && !props.getIssuer().isBlank()) {
            builder.issuer(props.getIssuer());
        }
        if (expiresAt != null) {
            builder.expiration(Date.from(expiresAt));
        }

        return builder
                .signWith(key)
                .compact();
    }

    @Override
    public JwtPrincipal validate(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token vazio");
        }

        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);

        Claims c = jws.getPayload();

        String subject = c.getSubject();
        String issuer  = c.getIssuer();
        Date iat       = c.getIssuedAt();
        Date exp       = c.getExpiration();

        // Campos customizados comuns: uid, tenant, roles, aud
        Long userId = null;
        Object uidObj = c.get("uid");
        if (uidObj instanceof Number n) {
            userId = n.longValue();
        } else if (uidObj instanceof String s) {
            try { userId = Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }

        String tenant = null;
        Object tenantObj = c.get("tenant");
        if (tenantObj instanceof String s && !s.isBlank()) tenant = s;

        List<String> roles = Optional.ofNullable(c.get("roles", List.class))
                .map(list -> (List<?>) list)
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toCollection(ArrayList::new));

        List<String> audience = Optional.ofNullable(c.get("aud", List.class))
                .map(list -> (List<?>) list)
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toCollection(ArrayList::new));

        return buildPrincipal(
                userId,
                subject,
                roles,
                tenant,
                issuer,
                audience,
                iat != null ? iat.toInstant() : null,
                exp != null ? exp.toInstant() : null
        );
    }

    private JwtPrincipal buildPrincipal(Long userId, String subject, List<String> roles,
                                        String tenant, String issuer, List<String> audience,
                                        Instant issuedAt, Instant expiresAt) {
        try {
            Constructor<?> c1 = JwtPrincipal.class.getDeclaredConstructor(
                    Long.class, String.class, List.class, String.class, String.class, List.class, Instant.class, Instant.class);
            c1.setAccessible(true);
            return (JwtPrincipal) c1.newInstance(userId, subject, roles, tenant, issuer, audience, issuedAt, expiresAt);
        } catch (NoSuchMethodException ignore) {
        } catch (Exception e) {
            throw new RuntimeException("Erro invocando construtor JwtPrincipal(8 args)", e);
        }

        try {
            Constructor<?> c2 = JwtPrincipal.class.getDeclaredConstructor(
                    String.class, Long.class, String.class, List.class, String.class, List.class, Instant.class, Instant.class);
            c2.setAccessible(true);
            return (JwtPrincipal) c2.newInstance(subject, userId, tenant, roles, issuer, audience, issuedAt, expiresAt);
        } catch (NoSuchMethodException ignore) {
        } catch (Exception e) {
            throw new RuntimeException("Erro invocando construtor JwtPrincipal(alt ordem)", e);
        }

        try {
            Method builder = JwtPrincipal.class.getMethod("builder");
            Object b = builder.invoke(null);
            callIfExists(b, "userId", Long.class, userId);
            callIfExists(b, "subject", String.class, subject);
            callIfExists(b, "roles", List.class, roles);
            callIfExists(b, "tenant", String.class, tenant);
            callIfExists(b, "issuer", String.class, issuer);
            callIfExists(b, "audience", List.class, audience);
            callIfExists(b, "issuedAt", Instant.class, issuedAt);
            callIfExists(b, "expiresAt", Instant.class, expiresAt);
            Method build = b.getClass().getMethod("build");
            return (JwtPrincipal) build.invoke(b);
        } catch (NoSuchMethodException ignore) {
        } catch (Exception e) {
            throw new RuntimeException("Erro usando JwtPrincipal.builder()", e);
        }

        throw new IllegalStateException("Não encontrei um construtor/builder compatível para JwtPrincipal.");
    }

    private static void callIfExists(Object target, String method, Class<?> type, Object arg) {
        try {
            Method m = target.getClass().getMethod(method, type);
            m.invoke(target, arg);
        } catch (NoSuchMethodException ignore) {
        } catch (Exception e) {
            throw new RuntimeException("Falha invocando builder." + method, e);
        }
    }
}
