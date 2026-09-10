package br.com.lojagenerica.core.venda;

/**
 * Só o comercial — substitui o antigo StatusPedido (9 valores misturando
 * pagamento/entrega/cancelamento numa coisa só, ver docs/CONTEXTO.md).
 * Satélites de entrega/pagamento de gateway (quando os módulos de entrega/
 * loja online forem repontados pra cá) têm seu próprio status, independente
 * deste.
 */
public enum StatusVenda {
    RASCUNHO,
    CONFIRMADA,
    CANCELADA
}
