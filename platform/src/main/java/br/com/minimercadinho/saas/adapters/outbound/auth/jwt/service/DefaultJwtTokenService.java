package br.com.minimercadinho.saas.adapters.outbound.auth.jwt.service;

import br.com.minimercadinho.saas.adapters.outbound.auth.config.JwtProperties;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.exception.InvalidJwtTokenException;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.exception.TokenExpiredException;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.model.TokenSubject;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Serviço responsável pela geração e validação de JWTs HMAC.
 */
@Service
public class DefaultJwtTokenService implements JwtTokenService {

    private final JwtProperties props;
    private final SecretKey key;

    public DefaultJwtTokenService(JwtProperties props) {
        this.props = props;
        String secret = Objects.requireNonNull(
                props.getSecret(),
                "jwt.secret (env JWT_SECRET) não pode ser nulo; defina JWT_SECRET no Railway antes de subir."
        );
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "jwt.secret (env JWT_SECRET) deve ter pelo menos 32 caracteres (HMAC); gere uma chave longa e configure JWT_SECRET no Railway."
            );
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generateAccessToken(TokenSubject subject, Map<String, Object> customClaims) {
        if (subject == null) throw new IllegalArgumentException("TokenSubject não pode ser nulo");

        String sub = resolveSubject(subject);
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofMinutes(props.getAccessTokenExpirationMinutes()));

        Map<String, Object> claims = new HashMap<>();
        if (customClaims != null) claims.putAll(customClaims);
        claims.putIfAbsent("typ", "access");
        if (props.getAudience() != null && !props.getAudience().isBlank()) {
            claims.put("aud", props.getAudience());
        }

        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(sub)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(claims)
                .signWith(key)
                .compact();
    }

    @Override
    public Map<String, Object> parseAndValidate(String jwt) {
        try {
            Jws<Claims> parsed = Jwts.parser()
                    .verifyWith(key)
                    .clockSkewSeconds(props.getClockSkewSeconds())
                    .build()
                    .parseSignedClaims(jwt);

            Claims claims = parsed.getPayload();

            // valida issuer
            if (claims.getIssuer() == null || !claims.getIssuer().equals(props.getIssuer())) {
                throw new InvalidJwtTokenException("Issuer inválido");
            }

            // valida audience (opcional)
            if (props.getAudience() != null && !props.getAudience().isBlank()) {
                Object audClaim = claims.get("aud");
                if (audClaim == null) throw new InvalidJwtTokenException("Audience ausente");

                if (audClaim instanceof String aud) {
                    if (!props.getAudience().equals(aud))
                        throw new InvalidJwtTokenException("Audience inválida");
                } else if (audClaim instanceof Collection<?> col &&
                           !col.contains(props.getAudience())) {
                    throw new InvalidJwtTokenException("Audience inválida");
                }
            }

            return new HashMap<>(claims);
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException("JWT expirado", e);
        } catch (SignatureException e) {
            throw new InvalidJwtTokenException("Assinatura inválida", e);
        } catch (RuntimeException e) {
            throw new InvalidJwtTokenException("Token JWT inválido", e);
        }
    }

    /** Resolve o campo "subject" a partir de qualquer tipo de objeto. */
    private String resolveSubject(TokenSubject subject) {
        if (subject instanceof Principal p && p.getName() != null) {
            return p.getName();
        }

        List<String> candidates = List.of(
                "getSubject", "subject",
                "getUsername", "username",
                "getEmail", "email",
                "getId", "id"
        );

        for (String methodName : candidates) {
            try {
                Method m = subject.getClass().getMethod(methodName);
                Object v = m.invoke(subject);
                if (v != null) return String.valueOf(v);
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                // ignora exceções de reflexão
            }
        }

        return subject.toString();
    }
}
