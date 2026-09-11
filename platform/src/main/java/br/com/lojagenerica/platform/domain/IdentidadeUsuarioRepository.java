package br.com.lojagenerica.platform.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentidadeUsuarioRepository extends JpaRepository<IdentidadeUsuario, Long> {

    List<IdentidadeUsuario> findByEmailIgnoreCaseAndAtivoTrue(String email);

    /** Localiza o índice de login pra redefinir senha — usuarioIdTenant+empresaId é estável mesmo se o e-mail mudasse. */
    Optional<IdentidadeUsuario> findByUsuarioIdTenantAndEmpresaId(Long usuarioIdTenant, Long empresaId);
}
