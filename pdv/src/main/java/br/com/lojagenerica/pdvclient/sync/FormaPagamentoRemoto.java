package br.com.lojagenerica.pdvclient.sync;

/** Espelha {@code FormaPagamentoSyncDTO} do servidor. */
public record FormaPagamentoRemoto(long id, String nome, String natureza, boolean afetaCaixa, boolean ativo) {
}
