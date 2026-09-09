package br.com.minimercadinho.saas.adapters.outbound.persistence.entity;

import br.com.minimercadinho.saas.domain.enums.EntregaParadaStatus;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "entrega_parada")
@Access(AccessType.FIELD)
public class EntregaParadaEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rota_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_entrega_parada_rota"))
    private EntregaRotaEntity rota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_entrega_parada_pedido"))
    private PedidoEntity pedido;

    @Column(name = "ordem_rota", nullable = false)
    private Integer ordem;

    @Column(name = "cliente_nome_snapshot", nullable = false, length = 120)
    private String clienteNomeSnapshot;

    @Column(name = "endereco_snapshot", nullable = false, length = 255)
    private String enderecoSnapshot;

    @Column(name = "codigo_entrega_snapshot", length = 6)
    private String codigoEntregaSnapshot;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EntregaParadaStatus status = EntregaParadaStatus.PENDENTE;

    @Column(name = "distancia_anterior_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanciaAnteriorKm = BigDecimal.ZERO;

    @Column(name = "distancia_acumulada_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanciaAcumuladaKm = BigDecimal.ZERO;

    @Column(name = "duracao_anterior_segundos")
    private Long duracaoAnteriorSegundos;

    @Column(name = "duracao_acumulada_segundos")
    private Long duracaoAcumuladaSegundos;

    @Column(name = "latitude", precision = 10, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(name = "confirmado_em")
    private LocalDateTime confirmadoEm;

    @Column(name = "motivo_falha", length = 120)
    private String motivoFalha;

    @Column(name = "observacao", length = 500)
    private String observacao;

    @Column(name = "forma_pagamento_recebida", length = 30)
    private String formaPagamentoRecebida;

    @Column(name = "pagamento_divergente", nullable = false)
    private Boolean pagamentoDivergente = Boolean.FALSE;

    @Column(name = "avaliacao_entrega")
    private Integer avaliacaoEntrega;

    @Column(name = "ocorrencias", length = 255)
    private String ocorrencias;

    @Column(name = "aproximando_notificado_em")
    private LocalDateTime aproximandoNotificadoEm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        if (status == null) {
            status = EntregaParadaStatus.PENDENTE;
        }
        if (distanciaAnteriorKm == null) {
            distanciaAnteriorKm = BigDecimal.ZERO;
        }
        if (distanciaAcumuladaKm == null) {
            distanciaAcumuladaKm = BigDecimal.ZERO;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        trimFields();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        trimFields();
    }

    private void trimFields() {
        if (clienteNomeSnapshot != null) {
            clienteNomeSnapshot = clienteNomeSnapshot.trim();
        }
        if (enderecoSnapshot != null) {
            enderecoSnapshot = enderecoSnapshot.trim();
        }
        if (codigoEntregaSnapshot != null) {
            codigoEntregaSnapshot = codigoEntregaSnapshot.trim();
        }
        if (motivoFalha != null) {
            motivoFalha = motivoFalha.trim();
        }
        if (observacao != null) {
            observacao = observacao.trim();
        }
        if (formaPagamentoRecebida != null) {
            formaPagamentoRecebida = formaPagamentoRecebida.trim();
        }
        if (ocorrencias != null) {
            ocorrencias = ocorrencias.trim();
        }
        if (pagamentoDivergente == null) {
            pagamentoDivergente = Boolean.FALSE;
        }
    }

    public Long getId() {
        return id;
    }

    public EntregaRotaEntity getRota() {
        return rota;
    }

    public void setRota(final EntregaRotaEntity rotaValue) {
        rota = rotaValue;
    }

    public PedidoEntity getPedido() {
        return pedido;
    }

    public void setPedido(final PedidoEntity pedidoValue) {
        pedido = pedidoValue;
    }

    public Integer getOrdem() {
        return ordem;
    }

    public void setOrdem(final Integer ordemValue) {
        ordem = ordemValue;
    }

    public String getClienteNomeSnapshot() {
        return clienteNomeSnapshot;
    }

    public void setClienteNomeSnapshot(final String clienteNomeSnapshotValue) {
        clienteNomeSnapshot = clienteNomeSnapshotValue;
    }

    public String getEnderecoSnapshot() {
        return enderecoSnapshot;
    }

    public void setEnderecoSnapshot(final String enderecoSnapshotValue) {
        enderecoSnapshot = enderecoSnapshotValue;
    }

    public String getCodigoEntregaSnapshot() {
        return codigoEntregaSnapshot;
    }

    public void setCodigoEntregaSnapshot(final String codigoEntregaSnapshotValue) {
        codigoEntregaSnapshot = codigoEntregaSnapshotValue;
    }

    public EntregaParadaStatus getStatus() {
        return status;
    }

    public void setStatus(final EntregaParadaStatus statusValue) {
        status = statusValue;
    }

    public BigDecimal getDistanciaAnteriorKm() {
        return distanciaAnteriorKm;
    }

    public void setDistanciaAnteriorKm(final BigDecimal distanciaAnteriorKmValue) {
        distanciaAnteriorKm = distanciaAnteriorKmValue;
    }

    public BigDecimal getDistanciaAcumuladaKm() {
        return distanciaAcumuladaKm;
    }

    public void setDistanciaAcumuladaKm(
            final BigDecimal distanciaAcumuladaKmValue
    ) {
        distanciaAcumuladaKm = distanciaAcumuladaKmValue;
    }

    public Long getDuracaoAnteriorSegundos() {
        return duracaoAnteriorSegundos;
    }

    public void setDuracaoAnteriorSegundos(final Long duracaoAnteriorSegundosValue) {
        duracaoAnteriorSegundos = duracaoAnteriorSegundosValue;
    }

    public Long getDuracaoAcumuladaSegundos() {
        return duracaoAcumuladaSegundos;
    }

    public void setDuracaoAcumuladaSegundos(
            final Long duracaoAcumuladaSegundosValue
    ) {
        duracaoAcumuladaSegundos = duracaoAcumuladaSegundosValue;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(final BigDecimal latitudeValue) {
        latitude = latitudeValue;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(final BigDecimal longitudeValue) {
        longitude = longitudeValue;
    }

    public LocalDateTime getConfirmadoEm() {
        return confirmadoEm;
    }

    public void setConfirmadoEm(final LocalDateTime confirmadoEmValue) {
        confirmadoEm = confirmadoEmValue;
    }

    public String getMotivoFalha() {
        return motivoFalha;
    }

    public void setMotivoFalha(final String motivoFalhaValue) {
        motivoFalha = motivoFalhaValue;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(final String observacaoValue) {
        observacao = observacaoValue;
    }

    public String getFormaPagamentoRecebida() {
        return formaPagamentoRecebida;
    }

    public void setFormaPagamentoRecebida(final String formaPagamentoRecebidaValue) {
        formaPagamentoRecebida = formaPagamentoRecebidaValue;
    }

    public Boolean getPagamentoDivergente() {
        return pagamentoDivergente;
    }

    public void setPagamentoDivergente(final Boolean pagamentoDivergenteValue) {
        pagamentoDivergente = pagamentoDivergenteValue;
    }

    public Integer getAvaliacaoEntrega() {
        return avaliacaoEntrega;
    }

    public void setAvaliacaoEntrega(final Integer avaliacaoEntregaValue) {
        avaliacaoEntrega = avaliacaoEntregaValue;
    }

    public String getOcorrencias() {
        return ocorrencias;
    }

    public void setOcorrencias(final String ocorrenciasValue) {
        ocorrencias = ocorrenciasValue;
    }

    public LocalDateTime getAproximandoNotificadoEm() {
        return aproximandoNotificadoEm;
    }

    public void setAproximandoNotificadoEm(
            final LocalDateTime aproximandoNotificadoEmValue
    ) {
        aproximandoNotificadoEm = aproximandoNotificadoEmValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EntregaParadaEntity that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
