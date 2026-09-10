package br.com.lojagenerica.pdv;

/** Tabela pequena, sem cursor — o PDV puxa a lista inteira a cada tick (ver PdvSyncService). */
public record FormaPagamentoSyncDTO(Long id, String nome, String natureza, boolean afetaCaixa, boolean ativo) {
}
