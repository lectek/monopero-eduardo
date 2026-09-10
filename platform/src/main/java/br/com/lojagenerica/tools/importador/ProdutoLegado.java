package br.com.lojagenerica.tools.importador;

/** Espelha uma linha de {@code products} no rbp.db do IMS (pname+pclr+pwt é a PK lá). */
public record ProdutoLegado(String nome, String cor, String peso, String quantidadeTexto,
                             String codigo, String descricao, Double preco) {

    /**
     * O IMS grava {@code pqt} como TEXT e faz {@code Integer.parseInt} nele
     * (armadilha documentada — ver docs/ROADMAP.md, Risco #3). Retorna 0 em
     * vez de lançar quando o valor está vazio/corrompido, pra um produto
     * com estoque ilegível não travar a importação inteira dos outros.
     */
    public int quantidadeOuZero() {
        if (quantidadeTexto == null || quantidadeTexto.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(quantidadeTexto.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
