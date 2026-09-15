package br.com.lojagenerica.security.admin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Segunda {@link SecurityFilterChain} do app — sessão própria pra
 * {@code /gestao/**} (login de funcionário/dono contra {@code core.acesso}),
 * separada da chain de cliente em
 * {@code adapters.inbound.web.security.SecurityConfig} (que continua
 * cuidando só de {@code /login}/{@code /minha-conta}). Com duas chains,
 * ambas precisam de {@code @Order} explícito — {@code @Order(1)} garante
 * que esta é avaliada primeiro pra qualquer request sob {@code /gestao/**}
 * (seu {@code securityMatcher}); tudo o mais cai na outra chain.
 */
@Configuration
public class AdminSecurityConfig {

    private final AdminAuthenticationProvider adminAuthenticationProvider;

    public AdminSecurityConfig(AdminAuthenticationProvider adminAuthenticationProvider) {
        this.adminAuthenticationProvider = adminAuthenticationProvider;
    }

    @Bean
    public AuthenticationManager adminAuthenticationManager() {
        return new ProviderManager(adminAuthenticationProvider);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain gestaoSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/gestao/**")
                .authenticationManager(adminAuthenticationManager())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/gestao/login").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/gestao/login")
                        .loginProcessingUrl("/gestao/login")
                        .usernameParameter("email")
                        .passwordParameter("senha")
                        .successHandler(new GestaoAuthenticationSuccessHandler())
                        .failureUrl("/gestao/login?erro")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/gestao/logout")
                        .logoutSuccessUrl("/gestao/login?saiu")
                        .permitAll());
        return http.build();
    }
}
