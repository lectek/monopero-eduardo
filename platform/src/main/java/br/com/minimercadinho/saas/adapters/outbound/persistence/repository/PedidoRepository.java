package br.com.minimercadinho.saas.adapters.outbound.persistence.repository;

import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.PedidoEntity;
import br.com.minimercadinho.saas.domain.enums.ModoEntrega;
import br.com.minimercadinho.saas.domain.enums.StatusPedido;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Versão mínima do PedidoRepository do ParaisoPet: só os métodos que o
 * módulo de entrega (e o futuro checkout) realmente usam. As queries de
 * relatório (faturamento por dia/mês, resumo por cliente etc.) do original
 * usam funções JPQL específicas de MySQL (year(), month(), function('date',...))
 * e não foram portadas — não fazem parte do escopo atual.
 */
public interface PedidoRepository extends JpaRepository<PedidoEntity, Long> {

    long countByStatus(StatusPedido status);

    @Query("""
           select distinct p
             from PedidoEntity p
             left join fetch p.cliente c
            where p.id in :ids
           """)
    List<PedidoEntity> buscarPorIdsComCliente(@Param("ids") List<Long> ids);

    @Query("""
           select p
             from PedidoEntity p
             left join fetch p.cliente c
            where p.modoEntrega = :modoEntrega
              and p.status in :statuses
            order by p.data asc
           """)
    List<PedidoEntity> listarPorModoEntregaEStatusComCliente(
            @Param("modoEntrega") ModoEntrega modoEntrega,
            @Param("statuses") List<StatusPedido> statuses,
            Pageable pageable
    );

    @Query("""
           select p
             from PedidoEntity p
             left join fetch p.cliente c
            where p.modoEntrega = :modoEntrega
              and p.status not in :excludedStatuses
            order by p.data asc
           """)
    List<PedidoEntity> listarPorModoEntregaExcluindoStatusComCliente(
            @Param("modoEntrega") ModoEntrega modoEntrega,
            @Param("excludedStatuses") List<StatusPedido> excludedStatuses,
            Pageable pageable
    );

    List<PedidoEntity> findByModoEntregaAndStatusInOrderByDataAsc(
            ModoEntrega modoEntrega,
            List<StatusPedido> statuses,
            Pageable pageable
    );

    Optional<PedidoEntity> findByGatewayExternalReference(String gatewayExternalReference);

    Optional<PedidoEntity> findByGatewayPaymentId(String gatewayPaymentId);

    /** Painel admin: todos os pedidos, mais recentes primeiro. */
    @Query("""
           select p
             from PedidoEntity p
             left join fetch p.cliente c
            order by p.data desc
           """)
    List<PedidoEntity> listarTodosComClienteOrderByDataDesc(Pageable pageable);

    /** "Minha conta" do cliente: só os pedidos dele, com itens (pra mostrar o que comprou). */
    @Query("""
           select distinct p
             from PedidoEntity p
             left join fetch p.itens i
            where p.cliente.id = :clienteId
            order by p.data desc
           """)
    List<PedidoEntity> listarPorClienteComItensOrderByDataDesc(@Param("clienteId") Long clienteId);
}
