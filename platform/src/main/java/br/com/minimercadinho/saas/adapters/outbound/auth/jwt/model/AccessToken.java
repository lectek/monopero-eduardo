package br.com.minimercadinho.saas.adapters.outbound.auth.jwt.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public class AccessToken
implements Serializable {
    private String token;
    private Instant expiresAt;

    public AccessToken() {
    }

    public AccessToken(String token, Instant expiresAt) {
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return this.token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getExpiresAt() {
        return this.expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccessToken)) {
            return false;
        }
        AccessToken that = (AccessToken)o;
        return Objects.equals(this.token, that.token) && Objects.equals(this.expiresAt, that.expiresAt);
    }

    public int hashCode() {
        return Objects.hash(this.token, this.expiresAt);
    }
}

