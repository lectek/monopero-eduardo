package br.com.minimercadinho.saas.adapters.outbound.persistence.repository;

import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.EntregaRotaEntity;
import br.com.minimercadinho.saas.domain.enums.EntregaRotaStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntregaRotaRepository
        extends JpaRepository<EntregaRotaEntity, Long> {

    long countByStatusIn(List<EntregaRotaStatus> statuses);

    List<EntregaRotaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * "Minhas rotas" do motoboy logado: as que já são dele (entregador = ele, qualquer
     * status) mais as que ainda não têm entregador e estão prontas pra sair
     * (PLANEJADA/DESPACHADA) — disponíveis pra ele "pegar" ao iniciar.
     */
    @Query("""
           select r
             from EntregaRotaEntity r
            where r.entregador.id = :motoboyId
               or (r.entregador is null and r.status in :statusesDisponiveis)
            order by r.createdAt desc
           """)
    List<EntregaRotaEntity> findMinhasOuDisponiveis(
            @Param("motoboyId") Long motoboyId,
            @Param("statusesDisponiveis") List<EntregaRotaStatus> statusesDisponiveis
    );
}
