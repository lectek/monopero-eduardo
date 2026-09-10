package br.com.lojagenerica.pdvclient.sync;

import java.util.List;

/** Espelha {@code PullResponse<ProdutoSyncDTO>} do servidor. */
public record PullResultado(List<ProdutoRemoto> itens, String proximoCursor, boolean temMais) {
}
