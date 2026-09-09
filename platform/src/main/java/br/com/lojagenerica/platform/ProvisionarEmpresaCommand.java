package br.com.lojagenerica.platform;

public record ProvisionarEmpresaCommand(
        String razaoSocial,
        String nomeFantasia,
        String documento,
        String subdominio,
        String administradorNome,
        String administradorEmail,
        String administradorSenha) {
}
