package br.com.lojagenerica.core.auditoria;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Ponto único de registro de auditoria. Publica um evento Spring que só é
 * gravado (ver {@link AuditoriaEventListener}) {@code BEFORE_COMMIT} da
 * transação atual — uma linha de auditoria nunca existe pra uma mudança que
 * sofreu rollback, e vice-versa (propriedade não-negociável, ver
 * docs/CONTEXTO.md).
 */
@Service
public class AuditoriaService {

    private final ApplicationEventPublisher eventPublisher;

    public AuditoriaService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void registrar(EventoAuditoria evento) {
        eventPublisher.publishEvent(evento);
    }
}
