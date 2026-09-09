package br.com.lojagenerica.adapters.outbound.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name="refresh_tokens", indexes={@Index(name="ix_refresh_token_user_tenant", columnList="user_id, tenant_id"), @Index(name="ix_refresh_token_expires_at", columnList="expires_at")}, uniqueConstraints={@UniqueConstraint(name="uk_refresh_token_token", columnNames={"token"})})
public class RefreshTokenEntity {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false, updatable=false)
    private Long id;
    @Column(name="user_id", nullable=false)
    private Long userId;
    @Column(name="tenant_id", nullable=false, length=100)
    private String tenantId;
    @Column(name="token", nullable=false, length=512)
    private String token;
    @Column(name="issued_at", nullable=false, columnDefinition="DATETIME(6)")
    private Instant issuedAt;
    @Column(name="expires_at", nullable=false, columnDefinition="DATETIME(6)")
    private Instant expiresAt;
    @Column(name="revoked_at", columnDefinition="DATETIME(6)")
    private Instant revokedAt;
    @Column(name="ip_address", length=64)
    private String ipAddress;
    @Column(name="user_agent", length=512)
    private String userAgent;

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

    public String getIpAddress() {
        return this.ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return this.userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RefreshTokenEntity)) {
            return false;
        }
        RefreshTokenEntity that = (RefreshTokenEntity)o;
        if (this.id != null && that.id != null) {
            return Objects.equals(this.id, that.id);
        }
        return Objects.equals(this.token, that.token);
    }

    public int hashCode() {
        return this.id != null ? Objects.hash(this.id) : Objects.hash(this.token);
    }

    public String toString() {
        return "RefreshTokenEntity{id=" + String.valueOf(this.id) + ", userId=" + String.valueOf(this.userId) + ", tenantId='" + this.tenantId + "', token='***redacted***'}";
    }
}

