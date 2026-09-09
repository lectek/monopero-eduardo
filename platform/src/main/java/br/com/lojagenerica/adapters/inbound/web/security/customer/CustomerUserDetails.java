package br.com.lojagenerica.adapters.inbound.web.security.customer;

import br.com.lojagenerica.adapters.outbound.persistence.entity.CustomerEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Não existe tabela de roles — todo CustomerEntity autenticado é simplesmente ROLE_CLIENTE. */
public class CustomerUserDetails implements UserDetails {

    private final CustomerEntity customer;

    public CustomerUserDetails(CustomerEntity customer) {
        this.customer = customer;
    }

    public CustomerEntity getCustomer() {
        return customer;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    @Override
    public String getPassword() {
        return customer.getSenhaHash();
    }

    @Override
    public String getUsername() {
        return customer.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
