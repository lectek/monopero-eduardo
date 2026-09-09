package br.com.lojagenerica.core.cadastro;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoMovimentacaoRepository extends JpaRepository<TipoMovimentacao, Long> {

    Optional<TipoMovimentacao> findByCodigo(String codigo);
}
