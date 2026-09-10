package br.com.lojagenerica.pdv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * O caixa físico — autenticação própria (API key), separada da
 * autenticação de usuário (JWT): o terminal sincroniza o backlog da
 * madrugada sem ninguém logado (ver docs/CONTEXTO.md).
 */
@Entity
@Table(name = "terminal")
public class Terminal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(name = "api_key_hash", nullable = false, unique = true, length = 64)
    private String apiKeyHash;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "pareado_em", nullable = false)
    private Instant pareadoEm = Instant.now();

    @Column(name = "ultimo_sync_em")
    private Instant ultimoSyncEm;

    protected Terminal() {
    }

    public Terminal(String nome, String apiKeyHash) {
        this.nome = nome;
        this.apiKeyHash = apiKeyHash;
    }

    public Long getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getNome() {
        return nome;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public Instant getUltimoSyncEm() {
        return ultimoSyncEm;
    }

    public void registrarSync() {
        this.ultimoSyncEm = Instant.now();
    }
}
