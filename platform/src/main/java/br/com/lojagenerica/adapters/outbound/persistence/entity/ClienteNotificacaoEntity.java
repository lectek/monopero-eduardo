package br.com.lojagenerica.adapters.outbound.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "cliente_notificacao")
public class ClienteNotificacaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cliente_notificacao_usuario"))
    private CustomerEntity usuario;

    @Column(name = "tipo", length = 40)
    private String tipo;

    @Column(name = "titulo", length = 120)
    private String titulo;

    @Column(name = "mensagem", length = 500)
    private String mensagem;

    @Column(name = "lida", nullable = false)
    private Boolean lida = Boolean.FALSE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lida == null) {
            lida = Boolean.FALSE;
        }
    }

    public Long getId() {
        return id;
    }

    public CustomerEntity getUsuario() {
        return usuario;
    }

    public void setUsuario(CustomerEntity usuario) {
        this.usuario = usuario;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public Boolean getLida() {
        return lida;
    }

    public void setLida(Boolean lida) {
        this.lida = lida;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
