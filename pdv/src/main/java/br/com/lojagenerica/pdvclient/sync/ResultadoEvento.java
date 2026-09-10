package br.com.lojagenerica.pdvclient.sync;

import java.util.UUID;

/** Espelha {@code ResultadoEventoResponse} do servidor. */
public record ResultadoEvento(UUID uuid, String status, Long servidorId, String erro) {

    public boolean aceitoOuDuplicado() {
        return "ACEITO".equals(status) || "DUPLICADO".equals(status);
    }
}
