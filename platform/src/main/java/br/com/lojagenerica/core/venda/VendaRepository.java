package br.com.lojagenerica.core.venda;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VendaRepository extends JpaRepository<Venda, Long> {

    Optional<Venda> findByUuid(UUID uuid);

    /** Itens + pagamentos já carregados — evita LazyInitializationException fora de transação. */
    @Query("select distinct v from Venda v left join fetch v.itens left join fetch v.pagamentos where v.id = :id")
    Optional<Venda> findByIdComItensEPagamentos(Long id);
}
