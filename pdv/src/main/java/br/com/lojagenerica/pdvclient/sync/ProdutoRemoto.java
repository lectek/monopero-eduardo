package br.com.lojagenerica.pdvclient.sync;

import java.time.Instant;

/** Espelha {@code ProdutoSyncDTO} do servidor — uma linha do pull de catálogo. */
public record ProdutoRemoto(long id, String nome, String codigoInterno, Double precoVenda,
                             Long unidadeEstoqueId, boolean controlaEstoque, String status, Instant atualizadoEm) {
}
