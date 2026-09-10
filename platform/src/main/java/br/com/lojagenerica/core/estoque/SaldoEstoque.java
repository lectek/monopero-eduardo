package br.com.lojagenerica.core.estoque;

import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.produto.Produto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Projeção cacheada, sempre derivável de sum(quantidade_base) do ledger —
 * nunca a fonte da verdade. Um job de reconciliação (Fase G+) recomputa e
 * reporta drift; esta tabela existe só por performance de leitura.
 */
@Entity
@Table(name = "saldo_estoque")
@IdClass(SaldoEstoqueId.class)
public class SaldoEstoque {

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "local_estoque_id")
    private LocalEstoque localEstoque;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal quantidade = BigDecimal.ZERO;

    @Column(name = "custo_medio", precision = 15, scale = 4)
    private BigDecimal custoMedio;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm = Instant.now();

    protected SaldoEstoque() {
    }

    public SaldoEstoque(Produto produto, LocalEstoque localEstoque) {
        this.produto = produto;
        this.localEstoque = localEstoque;
    }

    public Produto getProduto() {
        return produto;
    }

    public LocalEstoque getLocalEstoque() {
        return localEstoque;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public BigDecimal getCustoMedio() {
        return custoMedio;
    }

    public void aplicar(BigDecimal quantidadeBaseAssinada, BigDecimal custoUnitarioEntrada) {
        BigDecimal saldoAnterior = this.quantidade;
        this.quantidade = this.quantidade.add(quantidadeBaseAssinada);

        // Custo médio ponderado — só recalcula em entradas com custo informado
        // (compra). Saída não muda custo médio, só reduz quantidade.
        if (quantidadeBaseAssinada.signum() > 0 && custoUnitarioEntrada != null) {
            BigDecimal custoAtual = this.custoMedio != null ? this.custoMedio : BigDecimal.ZERO;
            BigDecimal valorAnterior = custoAtual.multiply(saldoAnterior);
            BigDecimal valorEntrada = custoUnitarioEntrada.multiply(quantidadeBaseAssinada);
            BigDecimal novoSaldo = saldoAnterior.add(quantidadeBaseAssinada);
            this.custoMedio = novoSaldo.signum() != 0
                    ? valorAnterior.add(valorEntrada).divide(novoSaldo, 4, java.math.RoundingMode.HALF_UP)
                    : custoAtual;
        }

        this.atualizadoEm = Instant.now();
    }
}
