package br.com.lojagenerica.core.entrega;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntregaParadaRepository extends JpaRepository<EntregaParada, Long> {

    List<EntregaParada> findByRotaIdOrderByOrdem(Long rotaId);

    @Query("select p.venda.id from EntregaParada p where p.status <> 'CANCELADA'")
    List<Long> findVendaIdsComParadaAtiva();

    /** Base do rastreio público (`/rastreio/{token}`) — sem autenticação, então não expõe mais do que a própria rota. */
    @Query("select distinct p from EntregaParada p join fetch p.rota r left join fetch r.paradas where p.tokenRastreio = :token")
    Optional<EntregaParada> findByTokenRastreioComRotaEParadas(@Param("token") UUID token);
}
