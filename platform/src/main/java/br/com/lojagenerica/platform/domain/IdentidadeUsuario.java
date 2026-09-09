package br.com.lojagenerica.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Índice de login global (schema "plataforma"): resolve "em qual(is)
 * empresa(s) esse e-mail existe" ANTES de saber o schema/tenant. Um e-mail
 * pode aparecer em N empresas — ex.: o dono, com acesso a todas.
 * {@code usuarioIdTenant} aponta pra {@code core.acesso.Usuario} dentro do
 * schema da empresa (sem FK cross-schema).
 */
@Entity
@Table(name = "identidade_usuario")
public class IdentidadeUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "usuario_id_tenant", nullable = false)
    private Long usuarioIdTenant;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    protected IdentidadeUsuario() {
    }

    public IdentidadeUsuario(String email, Long empresaId, Long usuarioIdTenant, String senhaHash) {
        this.email = email;
        this.empresaId = empresaId;
        this.usuarioIdTenant = usuarioIdTenant;
        this.senhaHash = senhaHash;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public Long getUsuarioIdTenant() {
        return usuarioIdTenant;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
