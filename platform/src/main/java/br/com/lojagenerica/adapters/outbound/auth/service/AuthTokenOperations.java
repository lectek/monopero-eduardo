package br.com.lojagenerica.adapters.outbound.auth.service;

import br.com.lojagenerica.adapters.outbound.auth.jwt.model.TokenPair;
import java.util.List;
import java.util.Map;

public interface AuthTokenOperations {
    public TokenPair issueTokens(Long var1, String var2, String var3, List<String> var4, String var5, String var6);

    public TokenPair refreshUsing(String var1, String var2, String var3);

    public void revokeRefreshToken(String var1);

    public void revokeAllForUser(Long var1, String var2);

    public Map<String, Object> validateAccessToken(String var1);

    public Map<String, Object> parseAccessToken(String var1);

    public void revokeAccessToken(String var1);
}

