package br.com.lojagenerica.core.produto;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoPrecoHistoricoRepository extends JpaRepository<ProdutoPrecoHistorico, Long> {

    List<ProdutoPrecoHistorico> findByProdutoIdOrderByVigenteDeDesc(Long produtoId);
}
