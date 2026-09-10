package br.com.lojagenerica.core.venda;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RegistrarVendaCommand(
        UUID uuid,
        CanalVenda canal,
        Long localEstoqueId,
        Long clienteId,
        Long terminalId,
        Long usuarioId,
        String usuarioEmail,
        BigDecimal descontoValor,
        List<ItemVendaCommand> itens,
        List<PagamentoVendaCommand> pagamentos) {

    public record ItemVendaCommand(Long produtoId, BigDecimal quantidade, Long unidadeId,
                                    BigDecimal precoUnitario, BigDecimal descontoValor) {
    }

    public record PagamentoVendaCommand(Long formaPagamentoId, BigDecimal valor,
                                         BigDecimal valorRecebido, BigDecimal troco) {
    }
}
