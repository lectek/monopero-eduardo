package br.com.minimercadinho.saas.adapters.inbound.web.security.customer;

import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.CustomerEntity;
import br.com.minimercadinho.saas.adapters.outbound.persistence.repository.CustomerRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptado do GoogleOAuth2UserService do Copa Insider (CopadoMundo) — mesma ideia
 * (achar ou criar o usuário pelo e-mail do Google), simplificado porque aqui não
 * existe tabela de roles: todo CustomerEntity autenticado é ROLE_CLIENTE.
 */
@Service
public class GoogleOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuth2UserService.class);
    private static final String ATTR_EMAIL = "email";

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final CustomerRepository customerRepository;

    public GoogleOAuth2UserService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User googleUser = delegate.loadUser(request);

        String email = googleUser.getAttribute(ATTR_EMAIL);
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("google_sem_email");
        }
        String emailNorm = email.trim().toLowerCase();

        customerRepository.findByEmailIgnoreCase(emailNorm).orElseGet(() -> {
            String nome = googleUser.getAttribute("name");
            CustomerEntity novo = new CustomerEntity();
            novo.setEmail(emailNorm);
            novo.setNome((nome == null || nome.isBlank()) ? emailNorm : nome.trim());
            novo.setSenhaHash(CustomerEntity.SENHA_PLACEHOLDER_PREFIX + UUID.randomUUID());
            log.info("[oauth2] novo cliente via Google: {}", emailNorm);
            return customerRepository.save(novo);
        });

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"));
        return new DefaultOAuth2User(authorities, googleUser.getAttributes(), ATTR_EMAIL);
    }
}
