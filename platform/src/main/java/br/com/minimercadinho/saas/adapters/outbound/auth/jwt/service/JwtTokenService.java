package br.com.minimercadinho.saas.adapters.outbound.auth.jwt.service;

import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.model.TokenSubject;
import java.util.Map;

public interface JwtTokenService {
    public String generateAccessToken(TokenSubject var1, Map<String, Object> var2);

    public Map<String, Object> parseAndValidate(String var1);
}

