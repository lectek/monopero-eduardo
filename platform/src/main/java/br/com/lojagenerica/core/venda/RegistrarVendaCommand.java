package br.com.lojagenerica.core.venda;

import br.com.lojagenerica.domain.enums.ModoEntrega;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * {@code modoEntrega}/{@code enderecoEntrega}: quando ENTREGA, {@code acrescimo}
 * é tratado como o valor do frete (ver {@code Venda#definirEntrega}) — é o que
 * torna a venda elegível pro módulo {@code core.entrega} (roteirização de
 * motoboy). {@code null}/{@code RETIRADA} preserva o comportamento antigo de
 * {@code acrescimo} como um acréscimo genérico.
 */
public record RegistrarVendaCommand(
        UUID uuid,
        CanalVenda canal,
        Long localEstoqueId,
        Long clienteId,
        Long terminalId,
        Long usuarioId,
        String usuarioEmail,
        BigDecimal descontoValor,
        BigDecimal acrescimo,
        ModoEntrega modoEntrega,
        String enderecoEntrega,
        List<ItemVendaCommand> itens,
        List<PagamentoVendaCommand> pagamentos) {

    public record ItemVendaCommand(Long produtoId, BigDecimal quantidade, Long unidadeId,
                                    BigDecimal precoUnitario, BigDecimal descontoValor) {
    }

    public record PagamentoVendaCommand(Long formaPagamentoId, BigDecimal valor,
                                         BigDecimal valorRecebido, BigDecimal troco) {
    }
}
