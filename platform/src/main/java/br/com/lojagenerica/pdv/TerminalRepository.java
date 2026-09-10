package br.com.lojagenerica.pdv;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerminalRepository extends JpaRepository<Terminal, Long> {

    Optional<Terminal> findByApiKeyHash(String apiKeyHash);
}
