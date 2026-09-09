package br.com.lojagenerica.adapters.outbound.persistence.repository;

import br.com.lojagenerica.adapters.outbound.persistence.entity.ClienteNotificacaoEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Versão mínima do ClienteNotificacaoRepository do ParaisoPet — só o que o
 * módulo de entrega usa (gravar notificação + listar por cliente). A busca
 * administrativa paginada do original não foi portada (fora de escopo agora).
 */
public interface ClienteNotificacaoRepository extends JpaRepository<ClienteNotificacaoEntity, Long> {

    @Query("""
            select n
              from ClienteNotificacaoEntity n
             where n.usuario.id = :customerId
             order by n.createdAt desc
            """)
    List<ClienteNotificacaoEntity> findByUsuarioId(@Param("customerId") Long customerId);

    long countByUsuarioIdAndLidaFalse(Long customerId);
}
