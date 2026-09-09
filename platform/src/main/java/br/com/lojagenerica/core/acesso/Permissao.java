package br.com.lojagenerica.core.acesso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * O catálogo de códigos é definido em código (ver {@link PermissaoCatalogo})
 * e sincronizado no boot ({@link PermissaoSyncRunner}) — o que é
 * configurável por tenant é o mapeamento papel→permissão, não o conjunto do
 * que o software sabe checar.
 */
@Entity
@Table(name = "permissao")
public class Permissao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String codigo;

    @Column(nullable = false, length = 50)
    private String modulo;

    @Column(length = 300)
    private String descricao;

    protected Permissao() {
    }

    public Permissao(String codigo, String modulo, String descricao) {
        this.codigo = codigo;
        this.modulo = modulo;
        this.descricao = descricao;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getModulo() {
        return modulo;
    }

    public String getDescricao() {
        return descricao;
    }
}
