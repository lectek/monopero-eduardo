package br.com.lojagenerica.pdvclient.local;

public record FormaPagamentoCache(long id, String nome, String natureza, boolean afetaCaixa, boolean ativo) {

    /** JOptionPane/JComboBox usam toString() pra renderizar quando não recebem um ListCellRenderer próprio. */
    @Override
    public String toString() {
        return nome;
    }
}
