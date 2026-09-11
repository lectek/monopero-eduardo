package br.com.lojagenerica.core.entrega;

import br.com.lojagenerica.core.acesso.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntregaRotaRepository extends JpaRepository<EntregaRota, Long> {

    @Query("select distinct r from EntregaRota r left join fetch r.paradas left join fetch r.entregador where r.id = :id")
    Optional<EntregaRota> findByIdComParadas(@Param("id") Long id);

    /**
     * Sempre com {@code left join fetch r.paradas left join fetch r.entregador}: as telas mostram
     * {@code rota.paradas.size()} e o Thymeleaf renderiza fora da
     * transação do controller — sem o fetch aqui, isso é
     * LazyInitializationException garantido (sem sessão Hibernate aberta
     * no momento do render).
     */
    @Query("select distinct r from EntregaRota r left join fetch r.paradas left join fetch r.entregador order by r.criadaEm desc")
    List<EntregaRota> buscarRecentesComParadas();

    @Query("select distinct r from EntregaRota r left join fetch r.paradas left join fetch r.entregador where r.entregador.id = :entregadorId order by r.criadaEm desc")
    List<EntregaRota> buscarPorEntregadorComParadas(@Param("entregadorId") Long entregadorId);

    @Query("select distinct r from EntregaRota r left join fetch r.paradas left join fetch r.entregador where r.status = :status order by r.criadaEm desc")
    List<EntregaRota> buscarPorStatusComParadas(@Param("status") StatusEntregaRota status);

    /**
     * Reivindicação atômica: só um motoboy consegue "ganhar" a corrida —
     * a query só afeta 1 linha se {@code status} ainda for PLANEJADA no
     * momento do UPDATE (evita dois motoboys assumindo a mesma rota, o
     * defeito identificado em todas as referências analisadas).
     */
    @Modifying
    @Query("update EntregaRota r set r.status = 'EM_EXECUCAO', r.entregador = :entregador, r.iniciadaEm = current_timestamp "
            + "where r.id = :id and r.status = 'PLANEJADA'")
    int reivindicarEIniciar(@Param("id") Long id, @Param("entregador") Usuario entregador);
}
