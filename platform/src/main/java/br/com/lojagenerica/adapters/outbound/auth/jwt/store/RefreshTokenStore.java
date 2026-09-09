package br.com.lojagenerica.adapters.outbound.auth.jwt.store;

import br.com.lojagenerica.adapters.outbound.auth.jwt.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenStore {
    public RefreshToken save(RefreshToken var1);

    public Optional<RefreshToken> findValidByToken(String var1);

    public void revokeByToken(String var1, Instant var2);

    public void revokeAllForUser(Long var1, String var2, Instant var3);

    public RefreshToken rotate(String var1, RefreshToken var2, Instant var3);

    public long deleteExpired(Instant var1);
}

