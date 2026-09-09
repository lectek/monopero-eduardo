package br.com.minimercadinho.saas.adapters.outbound.persistence.repository;

import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.EntregaParadaEntity;
import br.com.minimercadinho.saas.domain.enums.EntregaParadaStatus;
import br.com.minimercadinho.saas.domain.enums.EntregaRotaStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntregaParadaRepository
        extends JpaRepository<EntregaParadaEntity, Long> {

    @Query("""
            select distinct ep.pedido.id
              from EntregaParadaEntity ep
              join ep.rota r
             where r.status in :statuses
            """)
    List<Long> findPedidoIdsInRouteStatuses(
            @Param("statuses") Collection<EntregaRotaStatus> statuses
    );

    @Query("""
            select ep
              from EntregaParadaEntity ep
              join fetch ep.pedido p
             where ep.rota.id = :rotaId
             order by ep.ordem asc
            """)
    List<EntregaParadaEntity> findByRotaIdWithPedidoOrderByOrdemAsc(
            @Param("rotaId") Long rotaId
    );

    @Query("""
            select ep
              from EntregaParadaEntity ep
              join fetch ep.rota r
             where ep.pedido.id = :pedidoId
               and r.status in :routeStatuses
            """)
    List<EntregaParadaEntity> findByPedidoIdInRouteStatuses(
            @Param("pedidoId") Long pedidoId,
            @Param("routeStatuses") Collection<EntregaRotaStatus> routeStatuses
    );

    @Query("""
            select ep
              from EntregaParadaEntity ep
             where ep.pedido.id = :pedidoId
               and ep.status in :statuses
             order by ep.confirmadoEm desc, ep.updatedAt desc, ep.id desc
            """)
    List<EntregaParadaEntity> findLatestByPedidoIdAndStatuses(
            @Param("pedidoId") Long pedidoId,
            @Param("statuses") Collection<EntregaParadaStatus> statuses,
            Pageable pageable
    );

    long countByRotaId(Long rotaId);

    long countByStatusInAndConfirmadoEmBetween(
            Collection<EntregaParadaStatus> statuses,
            LocalDateTime de,
            LocalDateTime ate
    );
}
