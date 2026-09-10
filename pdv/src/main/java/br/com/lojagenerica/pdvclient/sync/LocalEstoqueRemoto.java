package br.com.lojagenerica.pdvclient.sync;

/** Espelha {@code LocalEstoqueSyncDTO} do servidor. */
public record LocalEstoqueRemoto(long id, String nome, String tipo, boolean principal, boolean ativo) {
}
