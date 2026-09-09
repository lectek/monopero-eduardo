package br.com.lojagenerica.adapters.outbound.persistence.jpa;

import br.com.lojagenerica.adapters.outbound.persistence.entity.AdminUserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUserEntity, Long> {
    Optional<AdminUserEntity> findByEmailIgnoreCase(String email);
}
