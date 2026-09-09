package br.com.minimercadinho.saas.adapters.outbound.persistence.repository;

import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.CustomerEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

    Optional<CustomerEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Usado pelo módulo de entrega (endereço padrão do cliente logado) — busca por email OU cpf normalizado. */
    @Query("""
        select c
          from CustomerEntity c
         where lower(c.email) = lower(:ident)
            or (c.cpf is not null and replace(replace(replace(c.cpf,'.',''),'-',''),' ','')
             = replace(replace(replace(:ident,'.',''),'-',''),' ',''))
    """)
    Optional<CustomerEntity> findByEmailOrCpf(@Param("ident") String ident);

    List<CustomerEntity> findAllByOrderByNomeAsc();
}
