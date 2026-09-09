package br.com.lojagenerica.core.acesso;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    /** Traz papeis + permissoes já carregados — usado no login (ver AuthController). */
    @Query("select distinct u from Usuario u "
            + "left join fetch u.papeis p "
            + "left join fetch p.permissoes "
            + "where u.id = :id")
    Optional<Usuario> findByIdComPapeisEPermissoes(Long id);
}
