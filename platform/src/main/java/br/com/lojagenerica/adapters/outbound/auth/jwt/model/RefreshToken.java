package br.com.lojagenerica.adapters.outbound.auth.jwt.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public class RefreshToken
implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long userId;
    private String tenantId;
    private String token;
    private Instant issuedAt;
    private Instant expiresAt;
    private Instant revokedAt;
    private String userAgent;
    private String ipAddress;

    public RefreshToken() {
    }

    public RefreshToken(Long id, Long userId, String tenantId, String token, Instant issuedAt, Instant expiresAt, Instant revokedAt, String userAgent, String ipAddress) {
        this.id = id;
        this.userId = userId;
        this.tenantId = tenantId;
        this.token = token;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    public static RefreshToken newToken(Long userId, String tenantId, String token, Instant issuedAt, Instant expiresAt, String userAgent, String ipAddress) {
        return new RefreshToken(null, userId, tenantId, token, issuedAt, expiresAt, null, userAgent, ipAddress);
    }

    public boolean isRevoked() {
        return this.revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return this.expiresAt != null && now.isAfter(this.expiresAt);
    }

    public boolean isValid(Instant now) {
        return !this.isRevoked() && !this.isExpired(now);
    }

    public void revoke(Instant at) {
        this.revokedAt = at == null ? Instant.now() : at;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return this.userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getToken() {
        return this.token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getIssuedAt() {
        return this.issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Instant getExpiresAt() {
        return this.expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return this.revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getUserAgent() {
        return this.userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getIpAddress() {
        return this.ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RefreshToken)) {
            return false;
        }
        RefreshToken that = (RefreshToken)o;
        if (this.id != null && that.id != null) {
            return Objects.equals(this.id, that.id);
        }
        return Objects.equals(this.token, that.token);
    }

    public int hashCode() {
        return this.id != null ? Objects.hash(this.id) : Objects.hash(this.token);
    }

    public String toString() {
        return "RefreshToken{id=" + String.valueOf(this.id) + ", userId=" + String.valueOf(this.userId) + ", tenantId='" + this.tenantId + "', token='***redacted***', issuedAt=" + String.valueOf(this.issuedAt) + ", expiresAt=" + String.valueOf(this.expiresAt) + ", revokedAt=" + String.valueOf(this.revokedAt) + ", userAgent='" + this.userAgent + "', ipAddress='" + this.ipAddress + "'}";
    }
}

