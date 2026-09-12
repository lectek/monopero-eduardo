package br.com.lojagenerica.core.entrega;

import br.com.lojagenerica.core.acesso.Usuario;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Uma rota é um lote de vendas (modo ENTREGA) que o motoboy percorre numa
 * única ida-e-volta — a ordem das paradas vem de
 * {@link br.com.lojagenerica.application.service.delivery.DeliveryRouteService}
 * (TSP exato via Held-Karp, já existente e reaproveitado, nunca reescrito).
 * {@link #percentualComissaoSnapshot} é capturado na criação: mudar a
 * configuração de comissão depois não pode alterar retroativamente o que já
 * foi combinado numa rota em andamento.
 */
@Entity
@Table(name = "entrega_rota")
public class EntregaRota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false, columnDefinition = "text")
    private String origem;

    @Column(name = "distancia_total_km", precision = 10, scale = 2)
    private BigDecimal distanciaTotalKm;

    @Column(name = "mapa_url", columnDefinition = "text")
    private String mapaUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEntregaRota status = StatusEntregaRota.PLANEJADA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entregador_id")
    private Usuario entregador;

    @Column(name = "criado_por_usuario_id")
    private Long criadoPorUsuarioId;

    @Column(name = "percentual_comissao_snapshot", nullable = false, precision = 6, scale = 3)
    private BigDecimal percentualComissaoSnapshot = BigDecimal.ZERO;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm = Instant.now();

    @Column(name = "iniciada_em")
    private Instant iniciadaEm;

    @Column(name = "finalizada_em")
    private Instant finalizadaEm;

    @Column(name = "cancelada_em")
    private Instant canceladaEm;

    @Column(name = "cancelamento_motivo", length = 500)
    private String cancelamentoMotivo;

    @Column(name = "localizacao_latitude")
    private Double localizacaoLatitude;

    @Column(name = "localizacao_longitude")
    private Double localizacaoLongitude;

    @Column(name = "localizacao_atualizada_em")
    private Instant localizacaoAtualizadaEm;

    @OneToMany(mappedBy = "rota", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordem")
    private List<EntregaParada> paradas = new ArrayList<>();

    protected EntregaRota() {
    }

    public EntregaRota(String origem, Long criadoPorUsuarioId, BigDecimal percentualComissaoSnapshot) {
        this.origem = origem;
        this.criadoPorUsuarioId = criadoPorUsuarioId;
        this.percentualComissaoSnapshot = percentualComissaoSnapshot != null ? percentualComissaoSnapshot : BigDecimal.ZERO;
    }

    public void adicionarParada(EntregaParada parada) {
        parada.pertencerA(this);
        this.paradas.add(parada);
    }

    public void definirCalculo(BigDecimal distanciaTotalKm, String mapaUrl) {
        this.distanciaTotalKm = distanciaTotalKm;
        this.mapaUrl = mapaUrl;
    }

    /**
     * Atribui o motoboy e inicia a rota. Não valida concorrência aqui — isso é
     * responsabilidade de {@code EntregaRotaService#iniciarRota}, que usa um
     * UPDATE condicional no banco pra garantir que só um motoboy consiga
     * "reivindicar" a rota (evita dois motoboys assumindo a mesma rota).
     */
    public void iniciar(Usuario entregador) {
        if (this.status != StatusEntregaRota.PLANEJADA) {
            throw new IllegalStateException("Rota " + id + " não está planejada (status atual: " + status + ")");
        }
        this.entregador = entregador;
        this.status = StatusEntregaRota.EM_EXECUCAO;
        this.iniciadaEm = Instant.now();
    }

    public void concluir() {
        this.status = StatusEntregaRota.CONCLUIDA;
        this.finalizadaEm = Instant.now();
    }

    public void cancelar(String motivo) {
        this.status = StatusEntregaRota.CANCELADA;
        this.canceladaEm = Instant.now();
        this.cancelamentoMotivo = motivo;
    }

    /** Ping de GPS do motoboy — sobrescreve a posição anterior, sem histórico (ver docs do módulo). */
    public void atualizarLocalizacao(double latitude, double longitude) {
        this.localizacaoLatitude = latitude;
        this.localizacaoLongitude = longitude;
        this.localizacaoAtualizadaEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getOrigem() {
        return origem;
    }

    public BigDecimal getDistanciaTotalKm() {
        return distanciaTotalKm;
    }

    public String getMapaUrl() {
        return mapaUrl;
    }

    public StatusEntregaRota getStatus() {
        return status;
    }

    public Usuario getEntregador() {
        return entregador;
    }

    public Long getCriadoPorUsuarioId() {
        return criadoPorUsuarioId;
    }

    public BigDecimal getPercentualComissaoSnapshot() {
        return percentualComissaoSnapshot;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public Instant getIniciadaEm() {
        return iniciadaEm;
    }

    public Instant getFinalizadaEm() {
        return finalizadaEm;
    }

    public Instant getCanceladaEm() {
        return canceladaEm;
    }

    public String getCancelamentoMotivo() {
        return cancelamentoMotivo;
    }

    public Double getLocalizacaoLatitude() {
        return localizacaoLatitude;
    }

    public Double getLocalizacaoLongitude() {
        return localizacaoLongitude;
    }

    public Instant getLocalizacaoAtualizadaEm() {
        return localizacaoAtualizadaEm;
    }

    public List<EntregaParada> getParadas() {
        return paradas;
    }
}
