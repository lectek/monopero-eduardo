package br.com.lojagenerica.domain.enums;

import java.util.Arrays;
import java.util.Optional;

public enum MotivoCancelamentoPedido {
    CLIENTE_SOLICITOU("Cliente solicitou"),
    PRODUTO_FORA_DO_PADRAO("Produto fora do padrao"),
    ESTOQUE_MENOR_QUE_1("Estoque menor que 1");

    private final String label;

    MotivoCancelamentoPedido(final String labelValue) {
        this.label = labelValue;
    }

    public String getLabel() {
        return label;
    }

    public static Optional<MotivoCancelamentoPedido> fromValue(
            final String value
    ) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        final String normalized = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(item -> item.name().equals(normalized))
                .findFirst();
    }
}
