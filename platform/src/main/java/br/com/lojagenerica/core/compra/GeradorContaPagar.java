package br.com.lojagenerica.core.compra;

/**
 * Porta pro módulo Financeiro (Fase E) plugar a geração real de conta a
 * pagar + parcelas (a partir de {@code compra.condicaoPagamento}) sem
 * {@link CompraService} precisar mudar quando isso acontecer.
 */
public interface GeradorContaPagar {

    void gerar(Compra compraConfirmada);
}
