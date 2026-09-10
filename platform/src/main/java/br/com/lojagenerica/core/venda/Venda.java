package br.com.lojagenerica.core.venda;

import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.parceiro.Cliente;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Substitui {@code PedidoEntity}/{@code StatusPedido} (o antigo enum de 9
 * valores misturava pagamento/entrega/cancelamento — aqui só o comercial,
 * ver {@link StatusVenda}). O storefront/checkout antigo ({@code CheckoutService},
 * {@code adapters.outbound.ims}) ainda não foi repontado pra este agregado
 * — coexistem por enquanto (ver docs/ROADMAP.md, Fase C).
 */
@Entity
@Table(name = "venda")
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(length = 50)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CanalVenda canal;

    @Column(name = "terminal_id")
    private Long terminalId;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "local_estoque_id")
    private LocalEstoque localEstoque;

    @Column(nullable = false)
    private Instant data = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusVenda status = StatusVenda.RASCUNHO;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "desconto_valor", nullable = false, precision = 15, scale = 4)
    private BigDecimal descontoValor = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal acrescimo = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    private String observacoes;

    @Column(name = "cancelada_em")
    private Instant canceladaEm;

    @Column(name = "cancelamento_motivo", length = 500)
    private String cancelamentoMotivo;

    @Column(name = "criado_offline", nullable = false)
    private boolean criadoOffline = false;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    // Set (não List) de propósito: JOIN FETCH simultâneo de duas coleções
    // List ("bag") joga MultipleBagFetchException no Hibernate — ver
    // VendaRepository.findByIdComItensEPagamentos.
    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id")
    private Set<ItemVenda> itens = new LinkedHashSet<>();

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id")
    private Set<VendaPagamento> pagamentos = new LinkedHashSet<>();

    protected Venda() {
    }

    public Venda(CanalVenda canal, LocalEstoque localEstoque, Cliente cliente, Long usuarioId) {
        this.canal = canal;
        this.localEstoque = localEstoque;
        this.cliente = cliente;
        this.usuarioId = usuarioId;
    }

    /** Idempotência: PDV/checkout gera o UUID no cliente antes de enviar (ver VendaService.registrar). */
    public void usarUuid(UUID uuid) {
        if (uuid != null) {
            this.uuid = uuid;
        }
    }

    public void adicionarItem(ItemVenda item) {
        item.pertencerA(this);
        this.itens.add(item);
    }

    public void adicionarPagamento(VendaPagamento pagamento) {
        pagamento.pertencerA(this);
        this.pagamentos.add(pagamento);
    }

    void recalcularTotais() {
        this.subtotal = itens.stream().map(ItemVenda::getTotalLinha).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.total = subtotal.subtract(descontoValor).add(acrescimo);
    }

    void aplicarDesconto(BigDecimal descontoValor) {
        this.descontoValor = descontoValor != null ? descontoValor : BigDecimal.ZERO;
    }

    /** Usado pelo checkout online pra embutir o frete no total (Venda não tem coluna própria de frete). */
    public void aplicarAcrescimo(BigDecimal acrescimo) {
        this.acrescimo = acrescimo != null ? acrescimo : BigDecimal.ZERO;
    }

    public void marcarConfirmada() {
        this.status = StatusVenda.CONFIRMADA;
    }

    public void cancelar(String motivo) {
        this.status = StatusVenda.CANCELADA;
        this.canceladaEm = Instant.now();
        this.cancelamentoMotivo = motivo;
    }

    public Long getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public CanalVenda getCanal() {
        return canal;
    }

    public Instant getData() {
        return data;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public LocalEstoque getLocalEstoque() {
        return localEstoque;
    }

    public StatusVenda getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDescontoValor() {
        return descontoValor;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getCanceladaEm() {
        return canceladaEm;
    }

    public String getCancelamentoMotivo() {
        return cancelamentoMotivo;
    }

    public Set<ItemVenda> getItens() {
        return itens;
    }

    public Set<VendaPagamento> getPagamentos() {
        return pagamentos;
    }
}
