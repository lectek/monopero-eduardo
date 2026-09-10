package br.com.lojagenerica.core.estoque;

import br.com.lojagenerica.core.cadastro.SentidoMovimentacao;
import java.math.BigDecimal;

public record RegistrarMovimentacaoCommand(
        Long produtoId,
        Long localEstoqueId,
        String tipoMovimentacaoCodigo,
        SentidoMovimentacao sentido,
        BigDecimal quantidade,
        Long unidadeId,
        BigDecimal custoUnitario,
        OrigemMovimentacao origemTipo,
        Long origemId,
        Long origemItemId,
        Long terminalId,
        Long usuarioId,
        String motivo) {
}
