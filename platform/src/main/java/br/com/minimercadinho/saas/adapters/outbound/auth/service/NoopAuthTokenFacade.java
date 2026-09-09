package br.com.minimercadinho.saas.adapters.outbound.auth.service;

import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.model.TokenPair;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@ConditionalOnProperty(prefix="jwt", name={"enabled"}, havingValue="false", matchIfMissing=false)
public class NoopAuthTokenFacade
implements AuthTokenOperations {
    private static RuntimeException disabled() {
        return new IllegalStateException("JWT est\u00e1 desabilitado neste profile");
    }

    @Override
    public TokenPair issueTokens(Long userId, String username, String tenantId, List<String> roles, String userAgent, String ip) {
        throw NoopAuthTokenFacade.disabled();
    }

    @Override
    public TokenPair refreshUsing(String refreshTokenValue, String userAgent, String ip) {
        throw NoopAuthTokenFacade.disabled();
    }

    @Override
    public void revokeRefreshToken(String refreshTokenValue) {
    }

    @Override
    public void revokeAllForUser(Long userId, String tenantId) {
    }

    @Override
    public Map<String, Object> validateAccessToken(String jwt) {
        throw NoopAuthTokenFacade.disabled();
    }

    @Override
    public Map<String, Object> parseAccessToken(String jwt) {
        throw NoopAuthTokenFacade.disabled();
    }

    @Override
    public void revokeAccessToken(String accessToken) {
    }
}

