package br.com.lojagenerica.core.venda;

import br.com.lojagenerica.domain.enums.ModoEntrega;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VendaRepository extends JpaRepository<Venda, Long> {

    Optional<Venda> findByUuid(UUID uuid);

    List<Venda> findByModoEntregaAndStatusOrderByDataAsc(ModoEntrega modoEntrega, StatusVenda status);

    /** Itens + pagamentos já carregados — evita LazyInitializationException fora de transação. */
    @Query("select distinct v from Venda v left join fetch v.itens left join fetch v.pagamentos where v.id = :id")
    Optional<Venda> findByIdComItensEPagamentos(Long id);
}
