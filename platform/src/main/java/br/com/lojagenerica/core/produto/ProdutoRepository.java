package br.com.lojagenerica.core.produto;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    /** Sincronização incremental (PDV offline, ver core.pdv) — cursor por atualizadoEm. */
    List<Produto> findByAtualizadoEmAfterOrderByAtualizadoEmAsc(Instant cursor, Pageable pageable);
}
