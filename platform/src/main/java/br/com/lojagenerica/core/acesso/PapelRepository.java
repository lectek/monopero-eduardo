package br.com.lojagenerica.core.acesso;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PapelRepository extends JpaRepository<Papel, Long> {

    Optional<Papel> findByNome(String nome);

    /** Traz {@code permissoes} já carregada — evita LazyInitializationException fora de transação. */
    @Query("select p from Papel p left join fetch p.permissoes where p.nome = :nome")
    Optional<Papel> findByNomeComPermissoes(String nome);

    /** Usado pela tela de gestão de papéis (lista mostra contagem de permissões). */
    @Query("select distinct p from Papel p left join fetch p.permissoes order by p.nome")
    List<Papel> findAllComPermissoes();

    @Query("select p from Papel p left join fetch p.permissoes where p.id = :id")
    Optional<Papel> findByIdComPermissoes(Long id);
}
