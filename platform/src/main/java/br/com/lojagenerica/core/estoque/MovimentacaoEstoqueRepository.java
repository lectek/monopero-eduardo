package br.com.lojagenerica.core.estoque;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimentacaoEstoqueRepository extends JpaRepository<MovimentacaoEstoque, Long> {

    List<MovimentacaoEstoque> findByProdutoIdOrderByOcorridoEmDesc(Long produtoId);

    Optional<MovimentacaoEstoque> findByOrigemTipoAndOrigemIdAndOrigemItemId(
            OrigemMovimentacao origemTipo, Long origemId, Long origemItemId);
}
