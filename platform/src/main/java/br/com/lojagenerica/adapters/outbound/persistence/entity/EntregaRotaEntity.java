package br.com.lojagenerica.adapters.outbound.persistence.entity;

import br.com.lojagenerica.domain.enums.EntregaRotaStatus;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "entrega_rota")
@Access(AccessType.FIELD)
public class EntregaRotaEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_operacao", nullable = false)
    private LocalDate dataOperacao;

    @Column(name = "origem", nullable = false, length = 255)
    private String origem;

    @Column(name = "distancia_total_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanciaTotalKm = BigDecimal.ZERO;

    @Column(name = "custo_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal custoTotal = BigDecimal.ZERO;

    @Column(name = "mapa_url", length = 1024)
    private String mapaUrl;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EntregaRotaStatus status = EntregaRotaStatus.PLANEJADA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entregador_usuario_id",
            foreignKey = @ForeignKey(name = "fk_entrega_rota_entregador"))
    private AdminUserEntity entregador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criada_por_usuario_id",
            foreignKey = @ForeignKey(name = "fk_entrega_rota_criada_por"))
    private AdminUserEntity criadaPor;

    @Column(name = "despachada_em")
    private LocalDateTime despachadaEm;

    @Column(name = "iniciada_em")
    private LocalDateTime iniciadaEm;

    @Column(name = "finalizada_em")
    private LocalDateTime finalizadaEm;

    @Column(name = "motorista_latitude", precision = 10, scale = 6)
    private BigDecimal motoristaLatitude;

    @Column(name = "motorista_longitude", precision = 10, scale = 6)
    private BigDecimal motoristaLongitude;

    @Column(name = "motorista_localizacao_em")
    private LocalDateTime motoristaLocalizacaoEm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "rota", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EntregaParadaEntity> paradas = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (dataOperacao == null) {
            dataOperacao = LocalDate.now();
        }
        if (status == null) {
            status = EntregaRotaStatus.PLANEJADA;
        }
        if (distanciaTotalKm == null) {
            distanciaTotalKm = BigDecimal.ZERO;
        }
        if (custoTotal == null) {
            custoTotal = BigDecimal.ZERO;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (origem != null) {
            origem = origem.trim();
        }
        if (mapaUrl != null) {
            mapaUrl = mapaUrl.trim();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (origem != null) {
            origem = origem.trim();
        }
        if (mapaUrl != null) {
            mapaUrl = mapaUrl.trim();
        }
    }

    public void addParada(final EntregaParadaEntity parada) {
        if (parada == null) {
            return;
        }
        paradas.add(parada);
        parada.setRota(this);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDataOperacao() {
        return dataOperacao;
    }

    public void setDataOperacao(final LocalDate dataOperacaoValue) {
        dataOperacao = dataOperacaoValue;
    }

    public String getOrigem() {
        return origem;
    }

    public void setOrigem(final String origemValue) {
        origem = origemValue;
    }

    public BigDecimal getDistanciaTotalKm() {
        return distanciaTotalKm;
    }

    public void setDistanciaTotalKm(final BigDecimal distanciaTotalKmValue) {
        distanciaTotalKm = distanciaTotalKmValue;
    }

    public BigDecimal getCustoTotal() {
        return custoTotal;
    }

    public void setCustoTotal(final BigDecimal custoTotalValue) {
        custoTotal = custoTotalValue;
    }

    public String getMapaUrl() {
        return mapaUrl;
    }

    public void setMapaUrl(final String mapaUrlValue) {
        mapaUrl = mapaUrlValue;
    }

    public EntregaRotaStatus getStatus() {
        return status;
    }

    public void setStatus(final EntregaRotaStatus statusValue) {
        status = statusValue;
    }

    public AdminUserEntity getEntregador() {
        return entregador;
    }

    public void setEntregador(final AdminUserEntity entregadorValue) {
        entregador = entregadorValue;
    }

    public AdminUserEntity getCriadaPor() {
        return criadaPor;
    }

    public void setCriadaPor(final AdminUserEntity criadaPorValue) {
        criadaPor = criadaPorValue;
    }

    public LocalDateTime getDespachadaEm() {
        return despachadaEm;
    }

    public void setDespachadaEm(final LocalDateTime despachadaEmValue) {
        despachadaEm = despachadaEmValue;
    }

    public LocalDateTime getIniciadaEm() {
        return iniciadaEm;
    }

    public void setIniciadaEm(final LocalDateTime iniciadaEmValue) {
        iniciadaEm = iniciadaEmValue;
    }

    public LocalDateTime getFinalizadaEm() {
        return finalizadaEm;
    }

    public void setFinalizadaEm(final LocalDateTime finalizadaEmValue) {
        finalizadaEm = finalizadaEmValue;
    }

    public BigDecimal getMotoristaLatitude() {
        return motoristaLatitude;
    }

    public void setMotoristaLatitude(final BigDecimal motoristaLatitudeValue) {
        motoristaLatitude = motoristaLatitudeValue;
    }

    public BigDecimal getMotoristaLongitude() {
        return motoristaLongitude;
    }

    public void setMotoristaLongitude(final BigDecimal motoristaLongitudeValue) {
        motoristaLongitude = motoristaLongitudeValue;
    }

    public LocalDateTime getMotoristaLocalizacaoEm() {
        return motoristaLocalizacaoEm;
    }

    public void setMotoristaLocalizacaoEm(
            final LocalDateTime motoristaLocalizacaoEmValue
    ) {
        motoristaLocalizacaoEm = motoristaLocalizacaoEmValue;
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

    public List<EntregaParadaEntity> getParadas() {
        return paradas;
    }

    public void setParadas(final List<EntregaParadaEntity> paradasValue) {
        paradas.clear();
        if (paradasValue != null) {
            paradasValue.forEach(this::addParada);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EntregaRotaEntity that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
