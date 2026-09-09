package br.com.lojagenerica.adapters.outbound.auth.jwt.config;

import br.com.lojagenerica.adapters.outbound.auth.config.JwtProperties;
import br.com.lojagenerica.adapters.outbound.auth.jwt.provider.DefaultTokenProvider;
import br.com.lojagenerica.adapters.outbound.auth.jwt.provider.JwtService;
import br.com.lojagenerica.adapters.outbound.auth.jwt.provider.TokenProvider;
import br.com.lojagenerica.adapters.outbound.auth.jwt.store.InMemoryRefreshTokenStore;
import br.com.lojagenerica.adapters.outbound.auth.jwt.store.InMemoryTokenBlacklist;
import br.com.lojagenerica.adapters.outbound.auth.jwt.store.RefreshTokenJpaStore;
import br.com.lojagenerica.adapters.outbound.auth.jwt.store.RefreshTokenStore;
import br.com.lojagenerica.adapters.outbound.auth.jwt.store.TokenBlacklist;
import br.com.lojagenerica.adapters.outbound.persistence.jpa.RefreshTokenJpaRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Sem @ConditionalOnProperty("jwt.enabled") — JWT não é opcional num sistema
// multiempresa (ver application.yml). Removido junto com NoopAuthTokenFacade.
@Configuration
@EnableConfigurationProperties(value={JwtProperties.class})
public class JwtAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(value={TokenProvider.class})
    public TokenProvider tokenProvider(JwtProperties props) {
        return new DefaultTokenProvider(props);
    }

    @Bean
    @ConditionalOnMissingBean(value={TokenBlacklist.class})
    public TokenBlacklist tokenBlacklist() {
        return new InMemoryTokenBlacklist();
    }

    @Bean
    @ConditionalOnProperty(name={"security.refresh.store"}, havingValue="in-memory", matchIfMissing=true)
    public RefreshTokenStore inMemoryStore(Clock clock) {
        return new InMemoryRefreshTokenStore(clock);
    }

    @Bean
    @ConditionalOnMissingBean(value={RefreshTokenStore.class})
    @ConditionalOnBean(value={RefreshTokenJpaRepository.class})
    public RefreshTokenStore jpaStore(RefreshTokenJpaRepository repository) {
        return new RefreshTokenJpaStore(repository);
    }

    @Bean
    @ConditionalOnMissingBean(value={JwtService.class})
    public JwtService jwtService(TokenProvider tokenProvider, JwtProperties props, RefreshTokenStore refreshStore, TokenBlacklist blacklist) {
        return new JwtService(tokenProvider, props, refreshStore, blacklist);
    }
}

