package br.com.lojagenerica.adapters.outbound.persistence.entity;

import br.com.lojagenerica.domain.enums.ModoEntrega;
import br.com.lojagenerica.domain.enums.MotivoCancelamentoPedido;
import br.com.lojagenerica.domain.enums.StatusPedido;
import br.com.lojagenerica.domain.enums.TipoPagamento;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Adaptado do PedidoEntity do ParaisoPet (mesmos campos de entrega e de
 * gateway de pagamento — inclusive os campos gateway_* que serão usados na
 * integração com Mercado Pago). Diferenças: {@code cliente} aponta pra
 * {@link CustomerEntity} (aqui só existe um tipo de cliente, ver nota em
 * CustomerEntity), e {@code itens} usa {@link ItemPedidoEntity} com snapshot
 * de produto por chave natural em vez de relação com um ProdutoEntity.
 */
@Entity
@Table(name = "pedido")
@EntityListeners(AuditingEntityListener.class)
public class PedidoEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private CustomerEntity cliente;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime data;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true, message = "Total não pode ser negativo")
    @Column(nullable = false)
    private BigDecimal total;

    /**
     * Frete cobrado nesse pedido (0 pra retirada) — separado do total pra dar pra
     * calcular a comissão do motoboy (percentual sobre o frete, não sobre a venda toda)
     * e a fatia do desenvolvedor sobre a entrega.
     */
    @Column(name = "valor_frete")
    private BigDecimal valorFrete;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedidoEntity> itens = new ArrayList<>();

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPedido status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pagamento", nullable = false)
    private TipoPagamento tipoPagamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_entrega")
    private ModoEntrega modoEntrega;

    @Column(name = "metodo_pagamento")
    private String metodoPagamento;

    @Column(name = "forma_pagamento_recebida")
    private String formaPagamentoRecebida;

    @Column(name = "pagamento_divergente", nullable = false)
    private boolean pagamentoDivergente;

    @Column(name = "avaliacao_cliente")
    private Integer avaliacaoCliente;

    @Column(name = "pagamento_recebido_em")
    private LocalDateTime pagamentoRecebidoEm;

    // ===== Campos de gateway (Mercado Pago) — mesmo modelo do ParaisoPet =====
    @Column(name = "gateway_provider")
    private String gatewayProvider;

    @Column(name = "gateway_owner_reference")
    private String gatewayOwnerReference;

    @Column(name = "gateway_preference_id")
    private String gatewayPreferenceId;

    @Column(name = "gateway_external_reference")
    private String gatewayExternalReference;

    @Column(name = "gateway_checkout_url")
    private String gatewayCheckoutUrl;

    @Column(name = "gateway_payment_id")
    private String gatewayPaymentId;

    @Column(name = "gateway_payment_status")
    private String gatewayPaymentStatus;

    @Column(name = "gateway_payment_status_detail")
    private String gatewayPaymentStatusDetail;

    @Column(name = "gateway_payment_updated_at")
    private LocalDateTime gatewayPaymentUpdatedAt;

    @Column(name = "gateway_payment_ticket_url")
    private String gatewayPaymentTicketUrl;

    @Lob
    @Column(name = "gateway_pix_qr_code")
    private String gatewayPixQrCode;

    @Lob
    @Column(name = "gateway_pix_qr_code_base64")
    private String gatewayPixQrCodeBase64;

    // ===== Entrega =====
    @Column(name = "endereco_entrega")
    private String enderecoEntrega;

    @Column(name = "codigo_entrega", length = 6)
    private String codigoEntrega;

    @Column(name = "codigo_entrega_gerado_em")
    private LocalDateTime codigoEntregaGeradoEm;

    @Column(name = "codigo_entrega_confirmado_em")
    private LocalDateTime codigoEntregaConfirmadoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelamento_motivo")
    private MotivoCancelamentoPedido cancelamentoMotivo;

    @Column(name = "cancelado_em")
    private LocalDateTime canceladoEm;

    /**
     * Novo em relação ao ParaisoPet: guarda de idempotência pra baixa de
     * estoque. O Mercado Pago pode reenviar o mesmo webhook de pagamento
     * mais de uma vez — sem isso, uma venda poderia descontar o estoque
     * (e gravar em sold_records, a mesma tabela do IMS) mais de uma vez.
     */
    @Column(name = "estoque_baixado", nullable = false)
    private boolean estoqueBaixado;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public void addItem(ItemPedidoEntity item) {
        if (item == null) return;
        itens.add(item);
        item.setPedido(this);
    }

    @PrePersist
    public void prePersist() {
        if (data == null) data = LocalDateTime.now();
        if (total == null) total = BigDecimal.ZERO;
        if (modoEntrega == null) modoEntrega = ModoEntrega.ENTREGA;
    }

    public Long getId() { return id; }

    public CustomerEntity getCliente() { return cliente; }
    public void setCliente(CustomerEntity cliente) { this.cliente = cliente; }

    public LocalDateTime getData() { return data; }
    public void setData(LocalDateTime data) { this.data = data; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public BigDecimal getValorFrete() { return valorFrete; }
    public void setValorFrete(BigDecimal valorFrete) { this.valorFrete = valorFrete; }

    public List<ItemPedidoEntity> getItens() { return itens; }
    public void setItens(List<ItemPedidoEntity> itens) {
        this.itens.clear();
        if (itens != null) itens.forEach(this::addItem);
    }

    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = status; }

    public TipoPagamento getTipoPagamento() { return tipoPagamento; }
    public void setTipoPagamento(TipoPagamento tipoPagamento) { this.tipoPagamento = tipoPagamento; }

    public ModoEntrega getModoEntrega() { return modoEntrega; }
    public void setModoEntrega(ModoEntrega modoEntrega) { this.modoEntrega = modoEntrega; }

    public String getMetodoPagamento() { return metodoPagamento; }
    public void setMetodoPagamento(String metodoPagamento) { this.metodoPagamento = metodoPagamento; }

    public String getFormaPagamentoRecebida() { return formaPagamentoRecebida; }
    public void setFormaPagamentoRecebida(String formaPagamentoRecebida) { this.formaPagamentoRecebida = formaPagamentoRecebida; }

    public boolean isPagamentoDivergente() { return pagamentoDivergente; }
    public void setPagamentoDivergente(boolean pagamentoDivergente) { this.pagamentoDivergente = pagamentoDivergente; }

    public Integer getAvaliacaoCliente() { return avaliacaoCliente; }
    public void setAvaliacaoCliente(Integer avaliacaoCliente) { this.avaliacaoCliente = avaliacaoCliente; }

    public LocalDateTime getPagamentoRecebidoEm() { return pagamentoRecebidoEm; }
    public void setPagamentoRecebidoEm(LocalDateTime pagamentoRecebidoEm) { this.pagamentoRecebidoEm = pagamentoRecebidoEm; }

    public String getGatewayProvider() { return gatewayProvider; }
    public void setGatewayProvider(String gatewayProvider) { this.gatewayProvider = gatewayProvider; }

    public String getGatewayOwnerReference() { return gatewayOwnerReference; }
    public void setGatewayOwnerReference(String gatewayOwnerReference) { this.gatewayOwnerReference = gatewayOwnerReference; }

    public String getGatewayPreferenceId() { return gatewayPreferenceId; }
    public void setGatewayPreferenceId(String gatewayPreferenceId) { this.gatewayPreferenceId = gatewayPreferenceId; }

    public String getGatewayExternalReference() { return gatewayExternalReference; }
    public void setGatewayExternalReference(String gatewayExternalReference) { this.gatewayExternalReference = gatewayExternalReference; }

    public String getGatewayCheckoutUrl() { return gatewayCheckoutUrl; }
    public void setGatewayCheckoutUrl(String gatewayCheckoutUrl) { this.gatewayCheckoutUrl = gatewayCheckoutUrl; }

    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public void setGatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; }

    public String getGatewayPaymentStatus() { return gatewayPaymentStatus; }
    public void setGatewayPaymentStatus(String gatewayPaymentStatus) { this.gatewayPaymentStatus = gatewayPaymentStatus; }

    public String getGatewayPaymentStatusDetail() { return gatewayPaymentStatusDetail; }
    public void setGatewayPaymentStatusDetail(String gatewayPaymentStatusDetail) { this.gatewayPaymentStatusDetail = gatewayPaymentStatusDetail; }

    public LocalDateTime getGatewayPaymentUpdatedAt() { return gatewayPaymentUpdatedAt; }
    public void setGatewayPaymentUpdatedAt(LocalDateTime gatewayPaymentUpdatedAt) { this.gatewayPaymentUpdatedAt = gatewayPaymentUpdatedAt; }

    public String getGatewayPaymentTicketUrl() { return gatewayPaymentTicketUrl; }
    public void setGatewayPaymentTicketUrl(String gatewayPaymentTicketUrl) { this.gatewayPaymentTicketUrl = gatewayPaymentTicketUrl; }

    public String getGatewayPixQrCode() { return gatewayPixQrCode; }
    public void setGatewayPixQrCode(String gatewayPixQrCode) { this.gatewayPixQrCode = gatewayPixQrCode; }

    public String getGatewayPixQrCodeBase64() { return gatewayPixQrCodeBase64; }
    public void setGatewayPixQrCodeBase64(String gatewayPixQrCodeBase64) { this.gatewayPixQrCodeBase64 = gatewayPixQrCodeBase64; }

    public String getEnderecoEntrega() { return enderecoEntrega; }
    public void setEnderecoEntrega(String enderecoEntrega) { this.enderecoEntrega = enderecoEntrega; }

    public String getCodigoEntrega() { return codigoEntrega; }
    public void setCodigoEntrega(String codigoEntrega) { this.codigoEntrega = codigoEntrega; }

    public LocalDateTime getCodigoEntregaGeradoEm() { return codigoEntregaGeradoEm; }
    public void setCodigoEntregaGeradoEm(LocalDateTime codigoEntregaGeradoEm) { this.codigoEntregaGeradoEm = codigoEntregaGeradoEm; }

    public LocalDateTime getCodigoEntregaConfirmadoEm() { return codigoEntregaConfirmadoEm; }
    public void setCodigoEntregaConfirmadoEm(LocalDateTime codigoEntregaConfirmadoEm) { this.codigoEntregaConfirmadoEm = codigoEntregaConfirmadoEm; }

    public MotivoCancelamentoPedido getCancelamentoMotivo() { return cancelamentoMotivo; }
    public void setCancelamentoMotivo(MotivoCancelamentoPedido cancelamentoMotivo) { this.cancelamentoMotivo = cancelamentoMotivo; }

    public LocalDateTime getCanceladoEm() { return canceladoEm; }
    public void setCanceladoEm(LocalDateTime canceladoEm) { this.canceladoEm = canceladoEm; }

    public boolean isEstoqueBaixado() { return estoqueBaixado; }
    public void setEstoqueBaixado(boolean estoqueBaixado) { this.estoqueBaixado = estoqueBaixado; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PedidoEntity that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
