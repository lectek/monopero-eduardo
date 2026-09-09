package br.com.lojagenerica.adapters.outbound.auth.jwt.store;

import java.time.Instant;

public interface TokenBlacklist {
    public boolean isBlacklisted(String var1);

    public void blacklist(String var1, Instant var2);

    default public void purgeExpired() {
    }

    @Deprecated
    default public void save(String tokenOrJti, Instant expiresAt) {
        this.blacklist(tokenOrJti, expiresAt);
    }

    @Deprecated
    default public boolean exists(String tokenOrJti) {
        return this.isBlacklisted(tokenOrJti);
    }
}

