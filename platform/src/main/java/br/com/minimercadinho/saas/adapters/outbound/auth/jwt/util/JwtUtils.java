package br.com.minimercadinho.saas.adapters.outbound.auth.jwt.util;

import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.exception.InvalidJwtTokenException;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.exception.TokenExpiredException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;

public final class JwtUtils {
    private static final Map<String, SecretKey> KEY_CACHE = new ConcurrentHashMap<String, SecretKey>();

    private JwtUtils() {
    }

    public static String generateToken(Map<String, Object> claims, String subject, Instant expiration, String secret) {
        return JwtUtils.generateToken(claims, subject, expiration, secret, Clock.systemUTC());
    }

    public static String generateToken(Map<String, Object> claims, String subject, Instant expiration, String secret, Clock clock) {
        Objects.requireNonNull(expiration, "expiration");
        SecretKey key = JwtUtils.keyFrom(secret);
        Date iat = Date.from(Instant.now(clock));
        return Jwts.builder().claims(claims == null ? Map.of() : claims).subject(subject).issuedAt(iat).expiration(Date.from(expiration)).signWith((Key)key).compact();
    }

    public static Jws<Claims> parseToken(String token, String secret) {
        return JwtUtils.parseToken(token, secret, 30L);
    }

    public static Jws<Claims> parseToken(String token, String secret, long clockSkewSeconds) {
        try {
            SecretKey key = JwtUtils.keyFrom(secret);
            return Jwts.parser().verifyWith(key).clockSkewSeconds(Math.max(0L, clockSkewSeconds)).build().parseSignedClaims((CharSequence)token);
        }
        catch (ExpiredJwtException e) {
            throw new TokenExpiredException("Token expirado.");
        }
        catch (JwtException e) {
            throw new InvalidJwtTokenException("Token inv\u00e1lido.");
        }
    }

    public static String stripBearer(String maybeBearer) {
        if (maybeBearer == null) {
            return null;
        }
        String p = "Bearer ";
        return maybeBearer.startsWith(p) ? maybeBearer.substring(p.length()) : maybeBearer;
    }

    private static SecretKey keyFrom(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret vazio");
        }
        return KEY_CACHE.computeIfAbsent(secret, s -> Keys.hmacShaKeyFor((byte[])s.getBytes(StandardCharsets.UTF_8)));
    }
}

