package br.com.lojagenerica.pdvclient.shared;

/** Espelha {@code br.com.lojagenerica.pdv.TipoEventoPdv} no servidor — precisa bater byte a byte no JSON. */
public enum TipoEventoPdv {
    VENDA_REGISTRADA,
    VENDA_CANCELADA
}
