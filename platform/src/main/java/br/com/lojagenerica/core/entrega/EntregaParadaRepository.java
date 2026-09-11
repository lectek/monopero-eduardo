package br.com.lojagenerica.core.entrega;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EntregaParadaRepository extends JpaRepository<EntregaParada, Long> {

    List<EntregaParada> findByRotaIdOrderByOrdem(Long rotaId);

    @Query("select p.venda.id from EntregaParada p where p.status <> 'CANCELADA'")
    List<Long> findVendaIdsComParadaAtiva();
}
