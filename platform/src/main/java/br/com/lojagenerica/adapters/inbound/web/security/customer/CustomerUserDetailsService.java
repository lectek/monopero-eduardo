package br.com.lojagenerica.adapters.inbound.web.security.customer;

import br.com.lojagenerica.adapters.outbound.persistence.repository.CustomerRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Login por e-mail+senha do cliente (formLogin) — Google entra por {@link GoogleOAuth2UserService}, não por aqui. */
@Service
public class CustomerUserDetailsService implements UserDetailsService {

    private final CustomerRepository customerRepository;

    public CustomerUserDetailsService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return customerRepository.findByEmailIgnoreCase(email)
                .map(CustomerUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Cliente não encontrado: " + email));
    }
}
