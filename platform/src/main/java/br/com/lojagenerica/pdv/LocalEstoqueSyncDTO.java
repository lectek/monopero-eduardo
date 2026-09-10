package br.com.lojagenerica.pdv;

/** Tabela pequena, sem cursor — o PDV puxa a lista inteira a cada tick (ver PdvSyncService). */
public record LocalEstoqueSyncDTO(Long id, String nome, String tipo, boolean principal, boolean ativo) {
}
