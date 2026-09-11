package br.com.lojagenerica.core.cadastro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Lista livre — "UN"/"KG"/"M"/"CX" são exemplos, nada é seedado (ver docs/CONTEXTO.md). */
@Entity
@Table(name = "unidade_medida")
public class UnidadeMedida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String descricao;

    @Column(name = "casas_decimais", nullable = false)
    private short casasDecimais = 0;

    @Column(nullable = false)
    private boolean fracionavel = false;

    @Column(nullable = false)
    private boolean ativo = true;

    protected UnidadeMedida() {
    }

    public UnidadeMedida(String codigo, String descricao, short casasDecimais, boolean fracionavel) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.casasDecimais = casasDecimais;
        this.fracionavel = fracionavel;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public short getCasasDecimais() {
        return casasDecimais;
    }

    public void setCasasDecimais(short casasDecimais) {
        this.casasDecimais = casasDecimais;
    }

    public boolean isFracionavel() {
        return fracionavel;
    }

    public void setFracionavel(boolean fracionavel) {
        this.fracionavel = fracionavel;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
