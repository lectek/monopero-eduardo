package br.com.lojagenerica.pdv;

import java.util.UUID;

public record ResultadoEventoResponse(UUID uuid, StatusEvento status, Long servidorId, String erro) {

    public enum StatusEvento {
        ACEITO, DUPLICADO, REJEITADO
    }

    static ResultadoEventoResponse aceito(UUID uuid, Long servidorId) {
        return new ResultadoEventoResponse(uuid, StatusEvento.ACEITO, servidorId, null);
    }

    static ResultadoEventoResponse duplicado(UUID uuid, Long servidorId) {
        return new ResultadoEventoResponse(uuid, StatusEvento.DUPLICADO, servidorId, null);
    }

    static ResultadoEventoResponse rejeitado(UUID uuid, String erro) {
        return new ResultadoEventoResponse(uuid, StatusEvento.REJEITADO, null, erro);
    }
}
