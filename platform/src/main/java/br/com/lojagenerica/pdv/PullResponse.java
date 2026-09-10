package br.com.lojagenerica.pdv;

import java.util.List;

public record PullResponse<T>(List<T> itens, String proximoCursor, boolean temMais) {
}
