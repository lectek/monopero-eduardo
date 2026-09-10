package br.com.lojagenerica.core.estoque;

import java.io.Serializable;
import java.util.Objects;

public class SaldoEstoqueId implements Serializable {

    private Long produto;
    private Long localEstoque;

    public SaldoEstoqueId() {
    }

    public SaldoEstoqueId(Long produto, Long localEstoque) {
        this.produto = produto;
        this.localEstoque = localEstoque;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SaldoEstoqueId that)) return false;
        return Objects.equals(produto, that.produto) && Objects.equals(localEstoque, that.localEstoque);
    }

    @Override
    public int hashCode() {
        return Objects.hash(produto, localEstoque);
    }
}
