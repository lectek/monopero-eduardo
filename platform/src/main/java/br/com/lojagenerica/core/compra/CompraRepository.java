package br.com.lojagenerica.core.compra;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    List<Compra> findByFornecedorId(Long fornecedorId);

    /** Traz itens já carregados — evita LazyInitializationException fora de transação (ex.: CompraController.buscar). */
    @Query("select distinct c from Compra c left join fetch c.itens where c.id = :id")
    Optional<Compra> findByIdComItens(Long id);
}
