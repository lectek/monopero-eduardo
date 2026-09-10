package br.com.lojagenerica.pdv.web;

import br.com.lojagenerica.pdv.TerminalService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pdv/terminais")
public class TerminalController {

    private final TerminalService terminalService;

    public TerminalController(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    /** A apiKey só aparece nesta resposta — o servidor guarda só o hash. Configure-a no PDV uma vez, na tela de Sincronização. */
    @PostMapping
    @PreAuthorize("hasAuthority('TERMINAL_GERENCIAR')")
    public ResponseEntity<TerminalService.TerminalCriadoResultado> criar(@RequestBody CriarTerminalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(terminalService.criar(request.nome()));
    }

    public record CriarTerminalRequest(@NotBlank String nome) {
    }
}
