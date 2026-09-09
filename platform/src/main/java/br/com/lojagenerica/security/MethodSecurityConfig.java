package br.com.lojagenerica.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** Habilita {@code @PreAuthorize("hasAuthority('X')")} nos controllers de /api/v1/**. */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
