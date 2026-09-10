package br.com.lojagenerica.pdv;

import java.math.BigDecimal;
import java.util.List;

public record VendaRegistradaPayload(
        Long localEstoqueId,
        Long clienteId,
        Long usuarioId,
        BigDecimal descontoValor,
        List<ItemPayload> itens,
        List<PagamentoPayload> pagamentos) {

    public record ItemPayload(Long produtoId, BigDecimal quantidade, Long unidadeId,
                               BigDecimal precoUnitario, BigDecimal descontoValor) {
    }

    public record PagamentoPayload(Long formaPagamentoId, BigDecimal valor,
                                    BigDecimal valorRecebido, BigDecimal troco) {
    }
}
