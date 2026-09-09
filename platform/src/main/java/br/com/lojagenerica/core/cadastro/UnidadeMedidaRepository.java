package br.com.lojagenerica.core.cadastro;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnidadeMedidaRepository extends JpaRepository<UnidadeMedida, Long> {

    Optional<UnidadeMedida> findByCodigo(String codigo);
}
