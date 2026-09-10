package br.com.lojagenerica.core.estoque;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SaldoEstoqueRepository extends JpaRepository<SaldoEstoque, SaldoEstoqueId> {

    List<SaldoEstoque> findByProdutoId(Long produtoId);

    /**
     * Lock pessimista: duas movimentações do mesmo produto/local concorrentes
     * não podem ler o mesmo saldo, ambas somarem e uma sobrescrever a outra —
     * a segunda transação espera a primeira commitar antes de ler.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SaldoEstoque s where s.produto.id = :produtoId and s.localEstoque.id = :localEstoqueId")
    Optional<SaldoEstoque> buscarParaAtualizar(Long produtoId, Long localEstoqueId);
}
