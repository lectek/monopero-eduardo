package br.com.lojagenerica.core.entrega;

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
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Relato de imprevisto/segurança feito pelo motoboy durante a rota — log
 * de verdade (nunca editado/sobrescrito, só ganha {@code resolvidoEm}),
 * separado da máquina de estado da parada de propósito: reportar uma
 * ocorrência não muda a rota nem bloqueia a próxima ação (diferente de
 * {@link EntregaParada#registrarFalha}). Gravidade fica denormalizada de
 * {@link TipoOcorrencia#isGrave()} pra permitir consultar "alertas
 * abertos" direto no banco, sem carregar tudo pra filtrar em memória.
 */
@Entity
@Table(name = "entrega_ocorrencia")
public class EntregaOcorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rota_id")
    private EntregaRota rota;

    /** A parada "atual" no momento do relato, se houver — pode ser nulo (ex.: a caminho, entre paradas). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parada_id")
    private EntregaParada parada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoOcorrencia tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gravidade gravidade;

    @Column(columnDefinition = "text")
    private String descricao;

    private Double latitude;

    private Double longitude;

    @Column(name = "criado_por_usuario_id")
    private Long criadoPorUsuarioId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "resolvido_em")
    private Instant resolvidoEm;

    @Column(name = "resolvido_por_usuario_id")
    private Long resolvidoPorUsuarioId;

    protected EntregaOcorrencia() {
    }

    public EntregaOcorrencia(EntregaRota rota, EntregaParada parada, TipoOcorrencia tipo, String descricao,
                              Double latitude, Double longitude, Long criadoPorUsuarioId) {
        this.rota = rota;
        this.parada = parada;
        this.tipo = tipo;
        this.gravidade = tipo.isGrave() ? Gravidade.GRAVE : Gravidade.NORMAL;
        this.descricao = descricao;
        this.latitude = latitude;
        this.longitude = longitude;
        this.criadoPorUsuarioId = criadoPorUsuarioId;
    }

    public void resolver(Long usuarioId) {
        this.resolvidoEm = Instant.now();
        this.resolvidoPorUsuarioId = usuarioId;
    }

    public boolean isResolvida() {
        return resolvidoEm != null;
    }

    public Long getId() {
        return id;
    }

    public EntregaRota getRota() {
        return rota;
    }

    public EntregaParada getParada() {
        return parada;
    }

    public TipoOcorrencia getTipo() {
        return tipo;
    }

    public Gravidade getGravidade() {
        return gravidade;
    }

    public String getDescricao() {
        return descricao;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Long getCriadoPorUsuarioId() {
        return criadoPorUsuarioId;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getResolvidoEm() {
        return resolvidoEm;
    }

    public Long getResolvidoPorUsuarioId() {
        return resolvidoPorUsuarioId;
    }

    public enum Gravidade {
        NORMAL, GRAVE
    }
}
