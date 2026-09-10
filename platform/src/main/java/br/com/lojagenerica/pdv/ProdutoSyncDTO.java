package br.com.lojagenerica.pdv;

import java.math.BigDecimal;
import java.time.Instant;

public record ProdutoSyncDTO(Long id, String nome, String codigoInterno, BigDecimal precoVenda,
                              Long unidadeEstoqueId, boolean controlaEstoque, String status, Instant atualizadoEm) {
}
