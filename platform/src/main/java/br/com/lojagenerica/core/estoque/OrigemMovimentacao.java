package br.com.lojagenerica.core.estoque;

/** Espelha o CHECK constraint de movimentacao_estoque.origem_tipo (V005). */
public enum OrigemMovimentacao {
    VENDA,
    COMPRA,
    DEVOLUCAO,
    ORCAMENTO,
    INVENTARIO,
    AJUSTE_MANUAL,
    TRANSFERENCIA,
    PEDIDO_ONLINE,
    IMPORTACAO
}
