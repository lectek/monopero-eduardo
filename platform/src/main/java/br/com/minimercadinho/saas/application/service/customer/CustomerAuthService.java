package br.com.minimercadinho.saas.application.service.customer;

import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.CustomerEntity;
import br.com.minimercadinho.saas.adapters.outbound.persistence.repository.CustomerRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de cliente com e-mail+senha. Se o e-mail já existe como conta "sem
 * senha" (criada no checkout como convidado, ou no primeiro login via Google —
 * ver {@link CustomerEntity#SENHA_PLACEHOLDER_PREFIX}), o cadastro "reivindica"
 * essa conta em vez de falhar com e-mail duplicado.
 */
@Service
public class CustomerAuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerAuthService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public CustomerEntity cadastrar(String nome, String email, String telefone, String senha) {
        String emailNorm = email.trim().toLowerCase();
        Optional<CustomerEntity> existente = customerRepository.findByEmailIgnoreCase(emailNorm);

        if (existente.isPresent()) {
            CustomerEntity cliente = existente.get();
            if (!cliente.getSenhaHash().startsWith(CustomerEntity.SENHA_PLACEHOLDER_PREFIX)) {
                throw new IllegalArgumentException("Este e-mail já tem cadastro. Faça login.");
            }
            cliente.setNome(nome);
            if (telefone != null && !telefone.isBlank()) {
                cliente.setTelefone(telefone);
            }
            cliente.setSenhaHash(passwordEncoder.encode(senha));
            return customerRepository.save(cliente);
        }

        CustomerEntity novo = new CustomerEntity();
        novo.setNome(nome);
        novo.setEmail(emailNorm);
        novo.setTelefone(telefone);
        novo.setSenhaHash(passwordEncoder.encode(senha));
        return customerRepository.save(novo);
    }
}
