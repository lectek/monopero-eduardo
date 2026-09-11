package br.com.lojagenerica.core.acesso;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Só ADMINISTRADOR é {@code sistema=true} (indeletável, sempre com todas as
 * permissões — sem isso um tenant poderia se autobloquear). Papéis como
 * "Gerente"/"Vendedor" do spec são exemplos de documentação, nunca criados
 * automaticamente (ver ProvisionamentoTenantService).
 */
@Entity
@Table(name = "papel")
public class Papel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nome;

    @Column(length = 500)
    private String descricao;

    @Column(nullable = false)
    private boolean sistema = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "papel_permissao",
            joinColumns = @JoinColumn(name = "papel_id"),
            inverseJoinColumns = @JoinColumn(name = "permissao_id"))
    private Set<Permissao> permissoes = new HashSet<>();

    /** Ex.: chave="desconto.percentual_maximo" valor="10". */
    @CollectionTable(name = "papel_restricao", joinColumns = @JoinColumn(name = "papel_id"),
            uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = {"papel_id", "chave"}))
    @MapKeyColumn(name = "chave")
    @Column(name = "valor", nullable = false, length = 200)
    @jakarta.persistence.ElementCollection(fetch = FetchType.LAZY)
    private Map<String, String> restricoes = new HashMap<>();

    protected Papel() {
    }

    public Papel(String nome, String descricao, boolean sistema) {
        this.nome = nome;
        this.descricao = descricao;
        this.sistema = sistema;
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

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public boolean isSistema() {
        return sistema;
    }

    public Set<Permissao> getPermissoes() {
        return permissoes;
    }

    public Map<String, String> getRestricoes() {
        return restricoes;
    }
}
