package br.com.lojagenerica.adapters.outbound.auth.jwt.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class TokenSubject
implements Serializable {
    private Long userId;
    private String username;
    private String tenantId;
    private List<String> roles;

    public TokenSubject() {
    }

    public TokenSubject(Long userId, String username, String tenantId, List<String> roles) {
        this.userId = userId;
        this.username = username;
        this.tenantId = tenantId;
        this.roles = roles;
    }

    public Long getUserId() {
        return this.userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public List<String> getRoles() {
        return this.roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TokenSubject)) {
            return false;
        }
        TokenSubject that = (TokenSubject)o;
        return Objects.equals(this.userId, that.userId) && Objects.equals(this.username, that.username) && Objects.equals(this.tenantId, that.tenantId) && Objects.equals(this.roles, that.roles);
    }

    public int hashCode() {
        return Objects.hash(this.userId, this.username, this.tenantId, this.roles);
    }
}

