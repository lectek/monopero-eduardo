package br.com.lojagenerica.application.config;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration(proxyBeanMethods=false)
@Profile(value={"!test"})
@ConditionalOnProperty(name={"app.jpa.auditing.enabled"}, havingValue="true", matchIfMissing=true)
@ConditionalOnMissingBean(name={"jpaAuditingHandler"})
@EnableJpaAuditing(dateTimeProviderRef="brasiliaDateTimeProvider")
public class JpaAuditingConfig {
    @Bean
    public DateTimeProvider brasiliaDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneId.of("America/Sao_Paulo")));
    }
}

