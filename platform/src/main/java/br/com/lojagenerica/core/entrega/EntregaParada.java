package br.com.lojagenerica.core.entrega;

import br.com.lojagenerica.core.venda.Venda;
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
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Uma parada = uma venda dentro de uma rota. Nome/endereço são snapshots
 * tirados na criação da rota — se o cliente ou a venda mudarem depois, a
 * parada não muda (estabilidade de auditoria), ver padrão idêntico em todas
 * as referências analisadas (MiniMercadinhoSaaS, SaúdeMaisFarma, multlektec).
 */
@Entity
@Table(name = "entrega_parada")
public class EntregaParada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rota_id")
    private EntregaRota rota;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id")
    private Venda venda;

    @Column(nullable = false)
    private int ordem;

    @Column(name = "cliente_nome_snapshot", nullable = false, length = 200)
    private String clienteNomeSnapshot;

    @Column(name = "endereco_entrega_snapshot", nullable = false, columnDefinition = "text")
    private String enderecoEntregaSnapshot;

    @Column(name = "codigo_entrega", nullable = false, length = 10)
    private String codigoEntrega;

    @Column(name = "valor_frete_snapshot", nullable = false, precision = 15, scale = 4)
    private BigDecimal valorFreteSnapshot;

    @Column(name = "distancia_anterior_km", precision = 10, scale = 2)
    private BigDecimal distanciaAnteriorKm;

    @Column(name = "distancia_acumulada_km", precision = 10, scale = 2)
    private BigDecimal distanciaAcumuladaKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatusEntregaParada status = StatusEntregaParada.PENDENTE;

    @Column(name = "chegou_em")
    private Instant chegouEm;

    @Column(name = "entregue_em")
    private Instant entregueEm;

    @Column(name = "forma_pagamento_recebida", length = 20)
    private String formaPagamentoRecebida;

    @Column(name = "pagamento_divergente", nullable = false)
    private boolean pagamentoDivergente;

    @Column(name = "avaliacao_entrega")
    private Integer avaliacaoEntrega;

    @Column(columnDefinition = "text")
    private String ocorrencias;

    @Column(name = "falha_motivo", length = 500)
    private String falhaMotivo;

    @Column(columnDefinition = "text")
    private String observacao;

    protected EntregaParada() {
    }

    public EntregaParada(Venda venda, int ordem, String clienteNomeSnapshot, String enderecoEntregaSnapshot,
                          String codigoEntrega, BigDecimal valorFreteSnapshot) {
        this.venda = venda;
        this.ordem = ordem;
        this.clienteNomeSnapshot = clienteNomeSnapshot;
        this.enderecoEntregaSnapshot = enderecoEntregaSnapshot;
        this.codigoEntrega = codigoEntrega;
        this.valorFreteSnapshot = valorFreteSnapshot != null ? valorFreteSnapshot : BigDecimal.ZERO;
    }

    void pertencerA(EntregaRota rota) {
        this.rota = rota;
    }

    public void definirDistancias(BigDecimal anteriorKm, BigDecimal acumuladaKm) {
        this.distanciaAnteriorKm = anteriorKm;
        this.distanciaAcumuladaKm = acumuladaKm;
    }

    public void regenerarCodigo(String novoCodigo) {
        this.codigoEntrega = novoCodigo;
    }

    /** Sequencial: só é chamado pra "a próxima parada acionável" (ver EntregaRotaService#findNextStop). */
    public void marcarACaminho() {
        this.status = StatusEntregaParada.A_CAMINHO;
    }

    public void marcarChegou() {
        if (this.status != StatusEntregaParada.A_CAMINHO) {
            throw new IllegalStateException("Parada " + id + " não está a caminho (status atual: " + status + ")");
        }
        this.status = StatusEntregaParada.CHEGOU;
        this.chegouEm = Instant.now();
    }

    public void confirmarEntrega(String formaPagamentoRecebida, boolean pagamentoDivergente,
                                  Integer avaliacaoEntrega, String ocorrencias, String observacao) {
        if (this.status != StatusEntregaParada.CHEGOU) {
            throw new IllegalStateException("Parada " + id + " ainda não teve chegada registrada (status atual: " + status + ")");
        }
        this.status = StatusEntregaParada.ENTREGUE;
        this.entregueEm = Instant.now();
        this.formaPagamentoRecebida = formaPagamentoRecebida;
        this.pagamentoDivergente = pagamentoDivergente;
        this.avaliacaoEntrega = avaliacaoEntrega;
        this.ocorrencias = ocorrencias;
        this.observacao = observacao;
    }

    public void registrarFalha(StatusEntregaParada falhaStatus, String motivo, String observacao) {
        if (falhaStatus != StatusEntregaParada.TENTATIVA_SEM_SUCESSO && falhaStatus != StatusEntregaParada.REAGENDAR) {
            throw new IllegalArgumentException("Status de falha inválido: " + falhaStatus);
        }
        if (this.status != StatusEntregaParada.CHEGOU && this.status != StatusEntregaParada.A_CAMINHO) {
            throw new IllegalStateException("Parada " + id + " não pode registrar falha nesse status: " + status);
        }
        this.status = falhaStatus;
        this.falhaMotivo = motivo;
        this.observacao = observacao;
    }

    public void cancelar() {
        this.status = StatusEntregaParada.CANCELADA;
    }

    public boolean isConcluida() {
        return status == StatusEntregaParada.ENTREGUE || status == StatusEntregaParada.CANCELADA
                || status == StatusEntregaParada.TENTATIVA_SEM_SUCESSO || status == StatusEntregaParada.REAGENDAR;
    }

    public Long getId() {
        return id;
    }

    public EntregaRota getRota() {
        return rota;
    }

    public Venda getVenda() {
        return venda;
    }

    public int getOrdem() {
        return ordem;
    }

    public String getClienteNomeSnapshot() {
        return clienteNomeSnapshot;
    }

    public String getEnderecoEntregaSnapshot() {
        return enderecoEntregaSnapshot;
    }

    public String getCodigoEntrega() {
        return codigoEntrega;
    }

    public BigDecimal getValorFreteSnapshot() {
        return valorFreteSnapshot;
    }

    public BigDecimal getDistanciaAnteriorKm() {
        return distanciaAnteriorKm;
    }

    public BigDecimal getDistanciaAcumuladaKm() {
        return distanciaAcumuladaKm;
    }

    public StatusEntregaParada getStatus() {
        return status;
    }

    public Instant getChegouEm() {
        return chegouEm;
    }

    public Instant getEntregueEm() {
        return entregueEm;
    }

    public String getFormaPagamentoRecebida() {
        return formaPagamentoRecebida;
    }

    public boolean isPagamentoDivergente() {
        return pagamentoDivergente;
    }

    public Integer getAvaliacaoEntrega() {
        return avaliacaoEntrega;
    }

    public String getOcorrencias() {
        return ocorrencias;
    }

    public String getFalhaMotivo() {
        return falhaMotivo;
    }

    public String getObservacao() {
        return observacao;
    }
}
