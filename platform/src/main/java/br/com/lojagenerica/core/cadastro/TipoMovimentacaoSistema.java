package br.com.lojagenerica.core.cadastro;

/**
 * As únicas linhas de {@code tipo_movimentacao} criadas automaticamente,
 * uma vez, no provisionamento de cada tenant — porque o código referencia
 * esses códigos diretamente (ver ProvisionamentoTenantService/
 * TenantBootstrapService). Tudo o mais em Cadastros é livre.
 */
public enum TipoMovimentacaoSistema {

    VENDA("VENDA", "Venda", SentidoMovimentacao.SAIDA, false, false),
    COMPRA("COMPRA", "Compra", SentidoMovimentacao.ENTRADA, false, true),
    DEVOLUCAO_CLIENTE("DEVOLUCAO_CLIENTE", "Devolução de cliente", SentidoMovimentacao.ENTRADA, false, false),
    DEVOLUCAO_FORNECEDOR("DEVOLUCAO_FORNECEDOR", "Devolução a fornecedor", SentidoMovimentacao.SAIDA, true, false),
    INVENTARIO("INVENTARIO", "Ajuste de inventário", SentidoMovimentacao.ENTRADA, true, false),
    TRANSFERENCIA_SAIDA("TRANSFERENCIA_SAIDA", "Transferência (saída)", SentidoMovimentacao.SAIDA, true, false),
    TRANSFERENCIA_ENTRADA("TRANSFERENCIA_ENTRADA", "Transferência (entrada)", SentidoMovimentacao.ENTRADA, true, false);

    private final String codigo;
    private final String nome;
    private final SentidoMovimentacao sentido;
    private final boolean exigeMotivo;
    private final boolean afetaCustoMedio;

    TipoMovimentacaoSistema(String codigo, String nome, SentidoMovimentacao sentido,
                             boolean exigeMotivo, boolean afetaCustoMedio) {
        this.codigo = codigo;
        this.nome = nome;
        this.sentido = sentido;
        this.exigeMotivo = exigeMotivo;
        this.afetaCustoMedio = afetaCustoMedio;
    }

    public TipoMovimentacao paraEntidade() {
        return new TipoMovimentacao(codigo, nome, sentido, true, exigeMotivo, afetaCustoMedio);
    }

    public String codigo() {
        return codigo;
    }
}
