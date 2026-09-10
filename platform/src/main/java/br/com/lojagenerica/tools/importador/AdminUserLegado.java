package br.com.lojagenerica.tools.importador;

/** Espelha uma linha de {@code admin_users} no rbp.db (gravada pelo IMS, tela "Acessos do site"). */
public record AdminUserLegado(String nome, String email, String senhaHashBcrypt, String role, boolean ativo) {
}
