package br.com.lojagenerica.core.auditoria;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    List<RegistroAuditoria> findByEntidadeAndEntidadeIdOrderByOcorridoEmDesc(String entidade, Long entidadeId);
}
