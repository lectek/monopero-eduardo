package br.com.lojagenerica.pdvclient.local;

public record LocalEstoqueCache(long id, String nome, String tipo, boolean principal, boolean ativo) {

    /** JComboBox usa toString() pra renderizar quando não recebe um ListCellRenderer próprio. */
    @Override
    public String toString() {
        return nome;
    }
}
