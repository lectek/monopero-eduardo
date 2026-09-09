package br.com.lojagenerica.core.auditoria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuditoriaEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaEventListener.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RegistroAuditoriaRepository registroAuditoriaRepository;

    public AuditoriaEventListener(RegistroAuditoriaRepository registroAuditoriaRepository) {
        this.registroAuditoriaRepository = registroAuditoriaRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void aoRegistrarEvento(EventoAuditoria evento) {
        AuditoriaContext.Dados quem = AuditoriaContext.get();
        RegistroAuditoria registro = new RegistroAuditoria(
                quem.usuarioId(), quem.usuarioEmail(), evento.evento(), evento.entidade(), evento.entidadeId(),
                paraJson(evento.valoresAntes()), paraJson(evento.valoresDepois()), evento.motivo());
        registroAuditoriaRepository.save(registro);
    }

    /**
     * Sem @EventListener aqui de propósito: sem transação ativa (ex.: teste
     * unitário publicando o evento direto), BEFORE_COMMIT nunca dispara e o
     * evento simplesmente não é gravado — aceitável, é um caso só de teste.
     */
    @EventListener
    void logarSeNaoHouverTransacao(EventoAuditoria evento) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            log.warn("EventoAuditoria publicado fora de transação, não será persistido: {}", evento);
        }
    }

    private String paraJson(Map<String, Object> valores) {
        if (valores == null || valores.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(valores);
        } catch (JsonProcessingException e) {
            log.error("Falha serializando valores de auditoria: {}", e.getMessage(), e);
            return null;
        }
    }
}
