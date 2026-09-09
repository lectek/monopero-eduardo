package br.com.lojagenerica.platform.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentidadeUsuarioRepository extends JpaRepository<IdentidadeUsuario, Long> {

    List<IdentidadeUsuario> findByEmailIgnoreCaseAndAtivoTrue(String email);
}
