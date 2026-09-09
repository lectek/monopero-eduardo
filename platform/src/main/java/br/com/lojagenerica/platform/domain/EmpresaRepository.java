package br.com.lojagenerica.platform.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    Optional<Empresa> findBySubdominio(String subdominio);

    Optional<Empresa> findBySchemaNome(String schemaNome);

    List<Empresa> findByStatus(StatusEmpresa status);
}
