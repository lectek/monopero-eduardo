package br.com.minimercadinho.saas.adapters.outbound.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Cliente que compra no site — separado de {@link AdminUserEntity} (equipe/admin).
 *
 * Unifica o que no ParaisoPet são duas entidades (UsuarioEntity, usada pelo
 * módulo de entrega para endereço/notificações, e ClienteEntity, referenciada
 * pelo pedido): aqui é um mercadinho único, não precisa da duplicação —
 * um só cliente cobre os dois papéis.
 */
@Entity
@Table(name = "customers")
@EntityListeners(AuditingEntityListener.class)
public class CustomerEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Prefixo de senha de clientes criados sem login (checkout como convidado,
     * ou primeiro login via Google) — nunca bate com nenhum hash bcrypt real,
     * então essa conta não consegue entrar com senha até o cliente "reivindicar"
     * o cadastro em /cadastro (ver CustomerAuthService).
     */
    public static final String SENHA_PLACEHOLDER_PREFIX = "SEM_SENHA_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    private String cpf;

    private String telefone;

    /** Endereço como texto único (mesmo modelo do UsuarioEntity do ParaisoPet — sem tabela de endereço separada). */
    private String endereco;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    @PreUpdate
    void normalize() {
        if (email != null) {
            email = email.trim().toLowerCase();
        }
        if (cpf != null) {
            cpf = cpf.replaceAll("\\D", "");
            if (cpf.isBlank()) {
                cpf = null;
            }
        }
        if (nome != null) {
            nome = nome.trim();
        }
        if (telefone != null) {
            telefone = telefone.trim();
        }
        if (endereco != null) {
            endereco = endereco.trim();
        }
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomerEntity that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
