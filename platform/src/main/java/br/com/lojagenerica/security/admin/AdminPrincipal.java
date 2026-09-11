package br.com.lojagenerica.security.admin;

/**
 * Principal autenticado da sessão de {@code /gestao/**} — carrega o schema
 * do tenant junto (diferente do login de cliente, que não precisa disso),
 * porque é o que {@code AdminTenantSessionFilter} usa pra restaurar o
 * {@code TenantContext} em toda requisição autenticada da sessão.
 */
public record AdminPrincipal(Long usuarioId, String nome, String email, String schema, String empresaNome) {

    /** Usado onde só o nome precisa aparecer (nav, saudação) — evita "AdminPrincipal[...]" no template. */
    @Override
    public String toString() {
        return nome;
    }
}
