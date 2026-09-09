package br.com.lojagenerica.adapters.outbound.auth.jwt.model;

import io.jsonwebtoken.Claims;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class JwtPrincipal
implements Serializable {
    private final String subject;
    private final Long userId;
    private final String tenant;
    private final List<String> roles;
    private final String issuer;
    private final List<String> audience;
    private final Instant expiresAt;
    private final String jti;
    private final Claims rawClaims;

    public JwtPrincipal(String subject, Long userId, String tenant, List<String> roles, String issuer, List<String> audience, Instant expiresAt, String jti, Claims rawClaims) {
        this.subject = subject;
        this.userId = userId;
        this.tenant = tenant;
        this.roles = roles;
        this.issuer = issuer;
        this.audience = audience;
        this.expiresAt = expiresAt;
        this.jti = jti;
        this.rawClaims = rawClaims;
    }

    public String getSubject() {
        return this.subject;
    }

    public Long getUserId() {
        return this.userId;
    }

    public String getTenant() {
        return this.tenant;
    }

    public List<String> getRoles() {
        return this.roles;
    }

    public String getIssuer() {
        return this.issuer;
    }

    public List<String> getAudience() {
        return this.audience;
    }

    public Instant getExpiresAt() {
        return this.expiresAt;
    }

    public String getJti() {
        return this.jti;
    }

    public Claims getRawClaims() {
        return this.rawClaims;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JwtPrincipal)) {
            return false;
        }
        JwtPrincipal that = (JwtPrincipal)o;
        return Objects.equals(this.subject, that.subject) && Objects.equals(this.userId, that.userId) && Objects.equals(this.tenant, that.tenant) && Objects.equals(this.roles, that.roles) && Objects.equals(this.issuer, that.issuer) && Objects.equals(this.audience, that.audience) && Objects.equals(this.expiresAt, that.expiresAt) && Objects.equals(this.jti, that.jti);
    }

    public int hashCode() {
        return Objects.hash(this.subject, this.userId, this.tenant, this.roles, this.issuer, this.audience, this.expiresAt, this.jti);
    }
}

