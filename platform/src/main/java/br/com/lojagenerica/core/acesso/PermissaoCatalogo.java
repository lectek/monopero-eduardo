package br.com.lojagenerica.core.acesso;

/**
 * O catálogo de permissões que o software sabe checar — fixo em código de
 * propósito (ver Papel/Permissao). O que é configurável por tenant é qual
 * papel tem qual permissão, não o conjunto de códigos existente. Cresce a
 * cada módulo novo — cada linha aqui deve corresponder a um
 * {@code @PreAuthorize} real em algum controller, nunca um código "pra usar
 * depois".
 */
public enum PermissaoCatalogo {

    CADASTRO_GERENCIAR("CADASTRO", "Criar/editar categorias, marcas, unidades, formas de pagamento etc."),

    PRODUTO_LER("PRODUTO", "Ver produtos cadastrados"),
    PRODUTO_ESCREVER("PRODUTO", "Criar/editar produtos"),
    PRODUTO_ALTERAR_PRECO("PRODUTO", "Alterar preço de venda ou custo de aquisição — auditado"),

    ESTOQUE_LER("ESTOQUE", "Ver saldo e histórico de movimentações"),
    ESTOQUE_AJUSTAR("ESTOQUE", "Registrar movimentação manual (perda, doação, ajuste) — auditado"),
    INVENTARIO_GERENCIAR("ESTOQUE", "Abrir, contar e finalizar inventário"),

    USUARIO_GERENCIAR("ACESSO", "Criar/editar usuários, papéis e permissões deste tenant");

    private final String modulo;
    private final String descricao;

    PermissaoCatalogo(String modulo, String descricao) {
        this.modulo = modulo;
        this.descricao = descricao;
    }

    public String codigo() {
        return name();
    }

    public String modulo() {
        return modulo;
    }

    public String descricao() {
        return descricao;
    }
}
