package br.com.lojagenerica.pdvclient.local;

import br.com.lojagenerica.pdvclient.shared.TipoEventoPdv;
import java.time.Instant;
import java.util.UUID;

public record EventoOutbox(long id, UUID eventoUuid, TipoEventoPdv tipo, Instant ocorridoEm,
                            String payloadJson, int tentativas) {
}
