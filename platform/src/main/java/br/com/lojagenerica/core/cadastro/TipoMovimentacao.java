package br.com.lojagenerica.core.cadastro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Linhas {@code sistema=true} (VENDA, COMPRA, DEVOLUCAO_*, INVENTARIO,
 * TRANSFERENCIA_*) são criadas pelo provisionamento e indeletáveis — o
 * código referencia esses códigos diretamente. Linhas de usuário (PERDA,
 * QUEBRA, DOACAO, BONIFICACAO, USO_INTERNO...) são cadastro livre — assim
 * "movimentação tipada" (física: ENTRADA/SAIDA) convive com "tipo de
 * movimentação é cadastro do usuário" (motivo: livre).
 */
@Entity
@Table(name = "tipo_movimentacao")
public class TipoMovimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SentidoMovimentacao sentido;

    @Column(nullable = false)
    private boolean sistema = false;

    @Column(name = "exige_motivo", nullable = false)
    private boolean exigeMotivo = false;

    @Column(name = "afeta_custo_medio", nullable = false)
    private boolean afetaCustoMedio = false;

    @Column(nullable = false)
    private boolean ativo = true;

    protected TipoMovimentacao() {
    }

    public TipoMovimentacao(String codigo, String nome, SentidoMovimentacao sentido, boolean sistema,
                             boolean exigeMotivo, boolean afetaCustoMedio) {
        this.codigo = codigo;
        this.nome = nome;
        this.sentido = sentido;
        this.sistema = sistema;
        this.exigeMotivo = exigeMotivo;
        this.afetaCustoMedio = afetaCustoMedio;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public SentidoMovimentacao getSentido() {
        return sentido;
    }

    public boolean isSistema() {
        return sistema;
    }

    public boolean isExigeMotivo() {
        return exigeMotivo;
    }

    public void setExigeMotivo(boolean exigeMotivo) {
        this.exigeMotivo = exigeMotivo;
    }

    public boolean isAfetaCustoMedio() {
        return afetaCustoMedio;
    }

    public void setAfetaCustoMedio(boolean afetaCustoMedio) {
        this.afetaCustoMedio = afetaCustoMedio;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
