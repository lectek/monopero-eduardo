package br.com.lojagenerica.pdvclient.local;

import java.time.Instant;

/** Espelho local e só-leitura de {@code core.produto.Produto} — atualizado pelo pull de sync. */
public record ProdutoCache(
        long id,
        String nome,
        String codigoInterno,
        Double precoVenda,
        Long unidadeEstoqueId,
        boolean controlaEstoque,
        String status,
        Instant atualizadoEm) {
}
