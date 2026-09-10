package br.com.lojagenerica.core.estoque;

import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.cadastro.SentidoMovimentacao;
import br.com.lojagenerica.core.cadastro.TipoMovimentacao;
import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.produto.Produto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ledger append-only — sem setters de propósito (nada aqui deve mudar
 * depois de criado; correção é uma nova linha compensatória, nunca uma
 * edição desta). UPDATE/DELETE são bloqueados por trigger de banco também
 * (V005__estoque.sql), então isto é reforço na camada de aplicação, não a
 * única barreira.
 */
@Entity
@Table(name = "movimentacao_estoque")
public class MovimentacaoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @ManyToOne(optional = false)
    @JoinColumn(name = "local_estoque_id")
    private LocalEstoque localEstoque;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tipo_movimentacao_id")
    private TipoMovimentacao tipoMovimentacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SentidoMovimentacao sentido;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal quantidade;

    @ManyToOne(optional = false)
    @JoinColumn(name = "unidade_id")
    private UnidadeMedida unidade;

    @Column(name = "fator_conversao", nullable = false, precision = 18, scale = 6)
    private BigDecimal fatorConversao = BigDecimal.ONE;

    @Column(name = "quantidade_base", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantidadeBase;

    @Column(name = "custo_unitario", precision = 15, scale = 4)
    private BigDecimal custoUnitario;

    @Column(name = "saldo_apos", precision = 18, scale = 6)
    private BigDecimal saldoApos;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_tipo", nullable = false, length = 30)
    private OrigemMovimentacao origemTipo;

    @Column(name = "origem_id")
    private Long origemId;

    @Column(name = "origem_item_id")
    private Long origemItemId;

    @Column(name = "terminal_id")
    private Long terminalId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(length = 500)
    private String motivo;

    @Column(name = "ocorrido_em", nullable = false)
    private Instant ocorridoEm = Instant.now();

    @Column(name = "registrado_em", nullable = false)
    private Instant registradoEm = Instant.now();

    protected MovimentacaoEstoque() {
    }

    MovimentacaoEstoque(Produto produto, LocalEstoque localEstoque, TipoMovimentacao tipoMovimentacao,
                        SentidoMovimentacao sentido, BigDecimal quantidade, UnidadeMedida unidade,
                        BigDecimal fatorConversao, BigDecimal quantidadeBase, BigDecimal custoUnitario,
                        BigDecimal saldoApos, OrigemMovimentacao origemTipo, Long origemId, Long origemItemId,
                        Long terminalId, Long usuarioId, String motivo) {
        this.produto = produto;
        this.localEstoque = localEstoque;
        this.tipoMovimentacao = tipoMovimentacao;
        this.sentido = sentido;
        this.quantidade = quantidade;
        this.unidade = unidade;
        this.fatorConversao = fatorConversao;
        this.quantidadeBase = quantidadeBase;
        this.custoUnitario = custoUnitario;
        this.saldoApos = saldoApos;
        this.origemTipo = origemTipo;
        this.origemId = origemId;
        this.origemItemId = origemItemId;
        this.terminalId = terminalId;
        this.usuarioId = usuarioId;
        this.motivo = motivo;
    }

    public Long getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Produto getProduto() {
        return produto;
    }

    public LocalEstoque getLocalEstoque() {
        return localEstoque;
    }

    public TipoMovimentacao getTipoMovimentacao() {
        return tipoMovimentacao;
    }

    public SentidoMovimentacao getSentido() {
        return sentido;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public BigDecimal getQuantidadeBase() {
        return quantidadeBase;
    }

    public BigDecimal getSaldoApos() {
        return saldoApos;
    }

    public OrigemMovimentacao getOrigemTipo() {
        return origemTipo;
    }

    public Long getOrigemId() {
        return origemId;
    }

    public String getMotivo() {
        return motivo;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }
}
