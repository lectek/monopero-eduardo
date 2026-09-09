package br.com.lojagenerica.core.auditoria;

import java.util.Map;

/**
 * Evento de negócio explícito (não diff de coluna) — quem gera isso descreve
 * o que aconteceu e por quê, não só "essa entidade mudou". Ver
 * docs/CONTEXTO.md sobre por que não é Hibernate Envers.
 */
public record EventoAuditoria(
        String evento,
        String entidade,
        Long entidadeId,
        Map<String, Object> valoresAntes,
        Map<String, Object> valoresDepois,
        String motivo) {

    public static EventoAuditoria de(String evento, String entidade, Long entidadeId,
                                      Map<String, Object> antes, Map<String, Object> depois, String motivo) {
        return new EventoAuditoria(evento, entidade, entidadeId, antes, depois, motivo);
    }
}
