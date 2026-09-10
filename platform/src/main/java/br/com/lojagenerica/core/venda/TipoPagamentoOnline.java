package br.com.lojagenerica.core.venda;

/** Só os métodos que o checkout online oferece — dinheiro/pagamento presencial não passa por aqui. */
public enum TipoPagamentoOnline {
    PIX,
    BOLETO,
    CARTAO_CREDITO,
    CARTAO_DEBITO
}
