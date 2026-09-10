package br.com.lojagenerica.pdv;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * {@code uuid} vira o {@code uuid} da própria Venda quando
 * {@code tipo=VENDA_REGISTRADA} — 1 evento offline = 1 venda, mesma chave
 * de idempotência nos dois lados (ver PdvSyncService).
 */
public record EventoPushRequest(UUID uuid, TipoEventoPdv tipo, Instant ocorridoEm, Map<String, Object> payload) {
}
