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

    FORNECEDOR_LER("FORNECEDOR", "Ver fornecedores cadastrados"),
    FORNECEDOR_ESCREVER("FORNECEDOR", "Criar/editar fornecedores"),

    COMPRA_LER("COMPRA", "Ver compras registradas"),
    COMPRA_CRIAR("COMPRA", "Criar rascunho de compra e adicionar itens"),
    COMPRA_CONFIRMAR("COMPRA", "Confirmar compra — gera entrada de estoque e atualiza custo — auditado"),

    CLIENTE_LER("CLIENTE", "Ver clientes cadastrados"),
    CLIENTE_ESCREVER("CLIENTE", "Criar/editar clientes"),

    VENDA_LER("VENDA", "Ver vendas registradas"),
    VENDA_CRIAR("VENDA", "Registrar venda — gera saída de estoque"),
    VENDA_CANCELAR("VENDA", "Cancelar venda confirmada — gera devolução de estoque — auditado"),

    USUARIO_GERENCIAR("ACESSO", "Criar/editar usuários, papéis e permissões deste tenant"),

    TERMINAL_GERENCIAR("PDV", "Parear/desativar terminais de PDV"),

    ENTREGA_GERENCIAR("ENTREGA", "Roteirizar vendas em entrega e acompanhar rotas"),
    ENTREGA_EXECUTAR("ENTREGA", "Assumir e executar rotas de entrega como motoboy");

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
