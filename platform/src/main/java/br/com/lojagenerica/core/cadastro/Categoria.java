package br.com.lojagenerica.core.cadastro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Árvore livre, profundidade arbitrária — nenhuma categoria fixa no código
 * (ver docs/CONTEXTO.md). {@code caminho} é um caminho materializado
 * (ex.: "/1/7/22/"), preenchido por {@link CategoriaService} — permite
 * "todo produto desta subárvore" com um {@code LIKE '/1/7/%'} em vez de CTE
 * recursiva a cada consulta.
 */
@Entity
@Table(name = "categoria")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @ManyToOne
    @JoinColumn(name = "categoria_pai_id")
    private Categoria categoriaPai;

    @Column(nullable = false, length = 500)
    private String caminho = "/";

    @Column(nullable = false)
    private short nivel = 0;

    @Column(nullable = false)
    private boolean ativo = true;

    protected Categoria() {
    }

    public Categoria(String nome, Categoria categoriaPai) {
        this.nome = nome;
        this.categoriaPai = categoriaPai;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Categoria getCategoriaPai() {
        return categoriaPai;
    }

    public String getCaminho() {
        return caminho;
    }

    public void definirCaminho(String caminho, short nivel) {
        this.caminho = caminho;
        this.nivel = nivel;
    }

    public short getNivel() {
        return nivel;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
