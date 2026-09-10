package br.com.lojagenerica.pdvclient.local;

public record ItemVendaLocal(long produtoId, String produtoNome, double quantidade, Long unidadeId,
                              double precoUnitario, Double descontoValor) {

    public double subtotal() {
        return quantidade * precoUnitario - (descontoValor == null ? 0 : descontoValor);
    }
}
