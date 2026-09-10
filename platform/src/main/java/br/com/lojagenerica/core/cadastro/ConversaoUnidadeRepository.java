package br.com.lojagenerica.core.cadastro;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversaoUnidadeRepository extends JpaRepository<ConversaoUnidade, Long> {

    /** Regra específica de um produto — resolvida antes da regra global (ver ConversaoUnidade). */
    Optional<ConversaoUnidade> findByUnidadeOrigemIdAndUnidadeDestinoIdAndProdutoId(
            Long unidadeOrigemId, Long unidadeDestinoId, Long produtoId);

    Optional<ConversaoUnidade> findByUnidadeOrigemIdAndUnidadeDestinoIdAndProdutoIdIsNull(
            Long unidadeOrigemId, Long unidadeDestinoId);
}
