package br.com.lojagenerica.core.cadastro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Resposta direta a "nem todo produto tem peso/marca/lote/validade/nº série"
 * — um tenant de material de construção define "Bitola", um de alimentos
 * define "Validade", sem mudança de schema nenhuma (ver docs/CONTEXTO.md).
 */
@Entity
@Table(name = "atributo_definicao")
public class AtributoDefinicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAtributo tipo;

    /** Só relevante quando tipo=LISTA: array JSON das opções válidas. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String opcoes;

    @Column(nullable = false)
    private boolean obrigatorio = false;

    @ManyToOne
    @JoinColumn(name = "aplicavel_categoria_id")
    private Categoria aplicavelCategoria;

    protected AtributoDefinicao() {
    }

    public AtributoDefinicao(String nome, TipoAtributo tipo, boolean obrigatorio) {
        this.nome = nome;
        this.tipo = tipo;
        this.obrigatorio = obrigatorio;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public TipoAtributo getTipo() {
        return tipo;
    }

    public String getOpcoes() {
        return opcoes;
    }

    public void setOpcoes(String opcoes) {
        this.opcoes = opcoes;
    }

    public boolean isObrigatorio() {
        return obrigatorio;
    }

    public Categoria getAplicavelCategoria() {
        return aplicavelCategoria;
    }

    public void setAplicavelCategoria(Categoria aplicavelCategoria) {
        this.aplicavelCategoria = aplicavelCategoria;
    }

    public enum TipoAtributo {
        TEXTO, NUMERO, DATA, BOOLEANO, LISTA
    }
}
