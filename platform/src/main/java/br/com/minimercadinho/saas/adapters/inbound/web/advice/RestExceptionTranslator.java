package br.com.minimercadinho.saas.adapters.inbound.web.advice;

import br.com.minimercadinho.saas.adapters.inbound.web.dto.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class RestExceptionTranslator {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetails> handleIllegalArgument(IllegalArgumentException ex,
                                                                HttpServletRequest req) {
        ProblemDetails body = new ProblemDetails();
        body.setStatus(HttpStatus.BAD_REQUEST.value());
        body.setError("Requisição inválida");
        body.setMessage(ex.getMessage());
        body.setPath(req.getRequestURI());
        body.setTimestamp(Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Adicionado em relação ao ParaisoPet: sem isto, IllegalStateException (ex.: "MP não configurado") virava 500 genérico. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetails> handleIllegalState(IllegalStateException ex, HttpServletRequest req) {
        ProblemDetails body = new ProblemDetails();
        body.setStatus(HttpStatus.CONFLICT.value());
        body.setError("Operação não pode ser concluída");
        body.setMessage(ex.getMessage());
        body.setPath(req.getRequestURI());
        body.setTimestamp(Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetails> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest req) {
        String details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + (fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "inválido"))
                .collect(Collectors.joining("; "));

        ProblemDetails body = new ProblemDetails();
        body.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value()); // 422 é comum para validação; use 400 se preferir
        body.setError("Erro de validação");
        body.setMessage(details);
        body.setPath(req.getRequestURI());
        body.setTimestamp(Instant.now());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    /**
     * Adicionado: sem isto, um ResponseStatusException(BAD_REQUEST, "...") lançado
     * dentro de AdminEntregaRouteService (validações de rota) caía no handler
     * genérico abaixo e virava 500 pro admin, escondendo o status real (400/404/409).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetails> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        ProblemDetails body = new ProblemDetails();
        body.setStatus(ex.getStatusCode().value());
        body.setError(ex.getStatusCode().toString());
        body.setMessage(ex.getReason());
        body.setPath(req.getRequestURI());
        body.setTimestamp(Instant.now());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleGeneric(Exception ex,
                                                        HttpServletRequest req) {
        ProblemDetails body = new ProblemDetails();
        body.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.setError("Erro interno do servidor");
        body.setMessage(ex.getMessage());
        body.setPath(req.getRequestURI());
        body.setTimestamp(Instant.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
