package br.com.lojagenerica.core.venda;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import org.springframework.data.domain.Persistable;

/**
 * Satélite 1:1 só para vendas do canal ONLINE — os 12 campos de gateway que
 * antes viviam direto em {@code PedidoEntity}. Uma venda de PDV nunca tem
 * linha aqui.
 *
 * <p>Implementa {@link Persistable} de propósito: com {@code @MapsId}, o ID
 * já vem preenchido (copiado da Venda) antes do primeiro save — sem isto o
 * Spring Data JPA acha que a entidade já existe (ID não-nulo) e tenta um
 * UPDATE em vez de INSERT, batendo 0 linhas e lançando
 * "Row was updated or deleted by another transaction" (StaleStateException).
 */
@Entity
@Table(name = "venda_pagamento_gateway")
public class VendaPagamentoGateway implements Persistable<Long> {

    @Id
    @Column(name = "venda_id")
    private Long vendaId;

    @OneToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @MapsId
    @jakarta.persistence.JoinColumn(name = "venda_id")
    private Venda venda;

    @Transient
    private boolean novo = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pagamento_online", nullable = false, length = 20)
    private TipoPagamentoOnline tipoPagamentoOnline;

    @Column(length = 30)
    private String provider;

    @Column(name = "preference_id", length = 100)
    private String preferenceId;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    @Column(name = "payment_id", length = 100)
    private String paymentId;

    @Column(name = "payment_status", length = 30)
    private String paymentStatus;

    @Column(name = "payment_status_detail", length = 100)
    private String paymentStatusDetail;

    @Column(name = "payment_updated_at")
    private Instant paymentUpdatedAt;

    @Column(name = "payment_ticket_url", length = 500)
    private String paymentTicketUrl;

    @Lob
    @Column(name = "pix_qr_code")
    private String pixQrCode;

    @Lob
    @Column(name = "pix_qr_code_base64")
    private String pixQrCodeBase64;

    @Column(name = "forma_pagamento_recebida", length = 60)
    private String formaPagamentoRecebida;

    @Column(name = "pagamento_divergente", nullable = false)
    private boolean pagamentoDivergente;

    @Column(name = "pagamento_recebido_em")
    private Instant pagamentoRecebidoEm;

    protected VendaPagamentoGateway() {
    }

    public VendaPagamentoGateway(Venda venda, TipoPagamentoOnline tipoPagamentoOnline) {
        this.venda = venda;
        this.vendaId = venda.getId();
        this.tipoPagamentoOnline = tipoPagamentoOnline;
    }

    @Override
    public Long getId() {
        return vendaId;
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    @PostPersist
    @PostLoad
    void marcarComoExistente() {
        this.novo = false;
    }

    public Long getVendaId() {
        return vendaId;
    }

    public Venda getVenda() {
        return venda;
    }

    public TipoPagamentoOnline getTipoPagamentoOnline() {
        return tipoPagamentoOnline;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getPreferenceId() {
        return preferenceId;
    }

    public void setPreferenceId(String preferenceId) {
        this.preferenceId = preferenceId;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getPaymentStatusDetail() {
        return paymentStatusDetail;
    }

    public void setPaymentStatusDetail(String paymentStatusDetail) {
        this.paymentStatusDetail = paymentStatusDetail;
    }

    public Instant getPaymentUpdatedAt() {
        return paymentUpdatedAt;
    }

    public void setPaymentUpdatedAt(Instant paymentUpdatedAt) {
        this.paymentUpdatedAt = paymentUpdatedAt;
    }

    public String getPaymentTicketUrl() {
        return paymentTicketUrl;
    }

    public void setPaymentTicketUrl(String paymentTicketUrl) {
        this.paymentTicketUrl = paymentTicketUrl;
    }

    public String getPixQrCode() {
        return pixQrCode;
    }

    public void setPixQrCode(String pixQrCode) {
        this.pixQrCode = pixQrCode;
    }

    public String getPixQrCodeBase64() {
        return pixQrCodeBase64;
    }

    public void setPixQrCodeBase64(String pixQrCodeBase64) {
        this.pixQrCodeBase64 = pixQrCodeBase64;
    }

    public void setFormaPagamentoRecebida(String formaPagamentoRecebida) {
        this.formaPagamentoRecebida = formaPagamentoRecebida;
    }

    public void setPagamentoDivergente(boolean pagamentoDivergente) {
        this.pagamentoDivergente = pagamentoDivergente;
    }

    public void setPagamentoRecebidoEm(Instant pagamentoRecebidoEm) {
        this.pagamentoRecebidoEm = pagamentoRecebidoEm;
    }

    public Instant getPagamentoRecebidoEm() {
        return pagamentoRecebidoEm;
    }
}
