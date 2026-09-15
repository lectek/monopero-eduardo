package br.com.lojagenerica.core.entrega;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntregaOcorrenciaRepository extends JpaRepository<EntregaOcorrencia, Long> {

    /**
     * {@code left join fetch o.parada}: o Thymeleaf lê {@code ocorrencia.parada.ordem}
     * (não só o id) fora da transação do controller (open-in-view desligado)
     * — sem o fetch aqui é LazyInitializationException garantido.
     */
    @Query("select o from EntregaOcorrencia o left join fetch o.parada where o.rota.id = :rotaId order by o.criadoEm desc")
    List<EntregaOcorrencia> findByRotaIdOrderByCriadoEmDesc(@Param("rotaId") Long rotaId);

    /** Alertas de segurança ainda sem resolução — é o que vira banner no admin. */
    List<EntregaOcorrencia> findByGravidadeAndResolvidoEmIsNullOrderByCriadoEmDesc(EntregaOcorrencia.Gravidade gravidade);
}
