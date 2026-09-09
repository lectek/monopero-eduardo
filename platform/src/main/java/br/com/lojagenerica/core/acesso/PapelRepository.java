package br.com.lojagenerica.core.acesso;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PapelRepository extends JpaRepository<Papel, Long> {

    Optional<Papel> findByNome(String nome);

    /** Traz {@code permissoes} já carregada — evita LazyInitializationException fora de transação. */
    @Query("select p from Papel p left join fetch p.permissoes where p.nome = :nome")
    Optional<Papel> findByNomeComPermissoes(String nome);
}
