package br.com.lojagenerica.adapters.outbound.auth.jwt.provider;

import br.com.lojagenerica.adapters.outbound.auth.jwt.model.JwtPrincipal;
import java.time.Instant;
import java.util.Map;

public interface TokenProvider {
    public String generateAccessToken(String var1, Map<String, Object> var2, Instant var3);

    public String generateRefreshToken(String var1, String var2, Map<String, Object> var3, Instant var4);

    public JwtPrincipal validate(String var1);
}

