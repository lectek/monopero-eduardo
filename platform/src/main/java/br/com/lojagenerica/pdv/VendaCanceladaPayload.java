package br.com.lojagenerica.pdv;

import java.util.UUID;

public record VendaCanceladaPayload(UUID vendaUuid, String motivo, Long usuarioId) {
}
