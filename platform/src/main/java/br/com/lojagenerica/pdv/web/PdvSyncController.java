package br.com.lojagenerica.pdv.web;

import br.com.lojagenerica.pdv.EventoPushRequest;
import br.com.lojagenerica.pdv.PdvSyncService;
import br.com.lojagenerica.pdv.PullResponse;
import br.com.lojagenerica.pdv.ResultadoEventoResponse;
import br.com.lojagenerica.security.TerminalAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticado por {@link TerminalAuthenticationFilter} (chave de API do
 * terminal, não JWT) — o caixa sincroniza sem ninguém logado.
 */
@RestController
@RequestMapping("/api/v1/pdv/sync")
public class PdvSyncController {

    private final PdvSyncService pdvSyncService;

    public PdvSyncController(PdvSyncService pdvSyncService) {
        this.pdvSyncService = pdvSyncService;
    }

    @PostMapping("/push")
    public PushResponse push(@RequestBody PushRequest request, HttpServletRequest httpRequest) {
        Long terminalId = (Long) httpRequest.getAttribute(TerminalAuthenticationFilter.ATTR_TERMINAL_ID);
        List<ResultadoEventoResponse> resultados = request.eventos().stream()
                .map(evento -> pdvSyncService.processar(terminalId, evento))
                .toList();
        return new PushResponse(resultados);
    }

    @GetMapping("/pull")
    public PullResponse<?> pull(
            @RequestParam String recurso,
            @RequestParam(required = false) String desde,
            @RequestParam(defaultValue = "500") int limite) {
        return pdvSyncService.pull(recurso, desde, limite);
    }

    public record PushRequest(List<EventoPushRequest> eventos) {
    }

    public record PushResponse(List<ResultadoEventoResponse> resultados) {
    }
}
