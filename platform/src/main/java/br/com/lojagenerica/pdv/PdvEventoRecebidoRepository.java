package br.com.lojagenerica.pdv;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PdvEventoRecebidoRepository extends JpaRepository<PdvEventoRecebido, Long> {

    Optional<PdvEventoRecebido> findByEventoUuid(UUID eventoUuid);
}
