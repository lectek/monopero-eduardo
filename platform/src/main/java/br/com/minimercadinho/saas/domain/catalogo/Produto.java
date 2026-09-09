package br.com.minimercadinho.saas.domain.catalogo;

/**
 * Produto tal como existe na tabela {@code products} do rbp.db — a mesma
 * fonte de verdade que o IMS usa no caixa. Chave natural (nome, cor, peso),
 * herdada do domínio original (Raj Blow Plast); "cor"/"peso" costumam vir
 * vazios ou reaproveitados livremente para o mercadinho.
 */
public record Produto(
        String nome,
        String cor,
        String peso,
        int quantidade,
        String codigoBarras,
        String descricao,
        double preco
) {
    /** Mesma regra de vitrine do ParaisoPet: só aparece pro cliente com preço e estoque positivos. */
    public boolean disponivelNaVitrine() {
        return preco > 0 && quantidade > 0;
    }
}
