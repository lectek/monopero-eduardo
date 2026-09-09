package br.com.minimercadinho.saas.adapters.outbound.auth.jwt.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public class TokenPair
implements Serializable {
    private final String accessToken;
    private final String refreshToken;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public TokenPair(String accessToken, String refreshToken, Instant issuedAt, Instant expiresAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public String getAccessToken() {
        return this.accessToken;
    }

    public String getRefreshToken() {
        return this.refreshToken;
    }

    public Instant getIssuedAt() {
        return this.issuedAt;
    }

    public Instant getExpiresAt() {
        return this.expiresAt;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TokenPair)) {
            return false;
        }
        TokenPair that = (TokenPair)o;
        return Objects.equals(this.accessToken, that.accessToken) && Objects.equals(this.refreshToken, that.refreshToken) && Objects.equals(this.issuedAt, that.issuedAt) && Objects.equals(this.expiresAt, that.expiresAt);
    }

    public int hashCode() {
        return Objects.hash(this.accessToken, this.refreshToken, this.issuedAt, this.expiresAt);
    }

    public String toString() {
        return "TokenPair{accessToken='[PROTECTED]', refreshToken='[PROTECTED]', issuedAt=" + String.valueOf(this.issuedAt) + ", expiresAt=" + String.valueOf(this.expiresAt) + "}";
    }
}

