package br.com.lojagenerica.adapters.inbound.web.security;

import br.com.lojagenerica.adapters.inbound.web.security.customer.GoogleOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * Filter chain do Spring Security — só para as rotas de CLIENTE (login/cadastro
 * com e-mail+senha e com Google). Adaptado (bem reduzido) do SecurityMvcConfig do
 * Copa Insider (CopadoMundo): mantido form login + Google OAuth2 + logout;
 * deliberadamente sem CSP customizado, rate-limit de login ou remember-me
 * persistente — esse projeto ainda não precisa dessa camada extra de robustez,
 * e cada peça a mais é mais uma coisa pra manter num mercadinho de bairro.
 *
 * /api/admin/** continua fora deste mecanismo: é protegido só pelo
 * AdminJwtAuthFilter (JWT próprio, sem sessão) — aqui só liberamos a rota pra
 * não colidir com esse filtro, sem exigir also autenticação de sessão nela.
 */
@Configuration
public class SecurityConfig {

    private final Environment environment;
    private final GoogleOAuth2UserService googleOAuth2UserService;

    public SecurityConfig(Environment environment, GoogleOAuth2UserService googleOAuth2UserService) {
        this.environment = environment;
        this.googleOAuth2UserService = googleOAuth2UserService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/webhooks/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/css/**", "/js/**", "/images/**", "/img/**", "/favicon.ico"
                ).permitAll()
                .requestMatchers(
                        "/", "/produtos", "/carrinho", "/checkout",
                        "/login", "/cadastro", "/logout", "/error",
                        "/politica-de-privacidade", "/termos-de-servico",
                        "/oauth2/**", "/login/oauth2/**"
                ).permitAll()
                .requestMatchers("/api/public/**", "/webhooks/**").permitAll()
                // Protegido pelo AdminJwtAuthFilter (com checagem de role ADMIN/MOTOBOY lá
                // dentro), não por sessão/login daqui.
                .requestMatchers("/api/admin/**", "/api/motoboy/**").permitAll()
                .requestMatchers("/minha-conta", "/minha-conta/**").hasRole("CLIENTE")
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                    .loginPage("/login")
                    .loginProcessingUrl("/login")
                    .usernameParameter("email")
                    .passwordParameter("senha")
                    .defaultSuccessUrl("/minha-conta", false)
                    .failureUrl("/login?error")
                    .permitAll()
            )
            .logout(logout -> logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/")
                    .permitAll()
            );

        if (StringUtils.hasText(environment.getProperty("spring.security.oauth2.client.registration.google.client-id"))) {
            http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/login")
                    .defaultSuccessUrl("/minha-conta", false)
                    .failureUrl("/login?error")
                    .userInfoEndpoint(ui -> ui.userService(googleOAuth2UserService))
            );
        }

        return http.build();
    }
}
