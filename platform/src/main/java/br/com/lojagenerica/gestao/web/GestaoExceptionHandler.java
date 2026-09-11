package br.com.lojagenerica.gestao.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Só pra {@code /gestao/**}: sem isto, qualquer exceção aqui cai no
 * {@code RestExceptionTranslator} global (é {@code @RestControllerAdvice},
 * sem restrição de pacote) e devolve JSON cru com HTTP 500 pro navegador —
 * péssimo pra uma tela que um humano está olhando. {@code @Order(HIGHEST_PRECEDENCE)}
 * garante que este advice é consultado primeiro pros controllers do pacote
 * {@code gestao.web}; pra qualquer exceção que ele não trate explicitamente,
 * a exceção segue pro próximo advice (o global) normalmente.
 */
@ControllerAdvice(basePackages = "br.com.lojagenerica.gestao.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GestaoExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GestaoExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    public ModelAndView naoEncontrado(NoSuchElementException ex, HttpServletRequest request) {
        return erro(request, HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Sem isto, uma negação de {@code @PreAuthorize} caía no
     * {@code RestExceptionTranslator} global — JSON cru numa tela que um
     * humano está olhando (e, antes do fix lá, virava HTTP 500 em vez de
     * 403 em qualquer lugar da aplicação, não só aqui).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView acessoNegado(AccessDeniedException ex, HttpServletRequest request) {
        return erro(request, HttpStatus.FORBIDDEN, "Você não tem permissão pra acessar esta página ou executar esta ação.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView duplicado(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violação de integridade em {}: {}", request.getRequestURI(), ex.getMessage());
        return erro(request, HttpStatus.CONFLICT,
                "Já existe um registro com esses dados (nome ou código duplicado), ou algum campo obrigatório ficou de fora.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ModelAndView validacaoFalhou(ConstraintViolationException ex, HttpServletRequest request) {
        String detalhes = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("; "));
        return erro(request, HttpStatus.BAD_REQUEST, "Dados inválidos: " + detalhes);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ModelAndView requisicaoInvalida(Exception ex, HttpServletRequest request) {
        return erro(request, HttpStatus.BAD_REQUEST, "Alguma informação do formulário ficou faltando ou num formato inválido.");
    }

    private ModelAndView erro(HttpServletRequest request, HttpStatus status, String mensagem) {
        String referer = request.getHeader("Referer");
        ModelAndView mv = new ModelAndView("pages/gestao/erro");
        mv.addObject("mensagem", mensagem);
        mv.addObject("voltar", referer != null ? referer : "/gestao");
        mv.setStatus(status);
        return mv;
    }
}
