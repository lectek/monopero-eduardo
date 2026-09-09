package br.com.minimercadinho.saas.adapters.outbound.auth.jwt.config;

import br.com.minimercadinho.saas.adapters.outbound.auth.config.JwtProperties;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.provider.DefaultTokenProvider;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.provider.JwtService;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.provider.TokenProvider;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.store.InMemoryRefreshTokenStore;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.store.InMemoryTokenBlacklist;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.store.RefreshTokenJpaStore;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.store.RefreshTokenStore;
import br.com.minimercadinho.saas.adapters.outbound.auth.jwt.store.TokenBlacklist;
import br.com.minimercadinho.saas.adapters.outbound.persistence.jpa.RefreshTokenJpaRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(value={JwtProperties.class})
@ConditionalOnProperty(prefix="jwt", name={"enabled"}, havingValue="true", matchIfMissing=false)
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

