package br.com.lojagenerica.core.cadastro;

/**
 * Classificação comportamental que o módulo de Caixa (Fase E) precisa —
 * o usuário cria as formas de pagamento, não os valores; isso só classifica
 * se dinheiro físico entra na gaveta ou não.
 */
public enum NaturezaFormaPagamento {
    DINHEIRO,
    CARTAO_CREDITO,
    CARTAO_DEBITO,
    PIX,
    BOLETO,
    TRANSFERENCIA,
    CREDITO_LOJA,
    OUTRO
}
