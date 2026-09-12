package br.com.lojagenerica.gestao.web;

import br.com.lojagenerica.core.entrega.EntregaRotaService;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Link público que o cliente recebe (WhatsApp/SMS, fora deste sistema por
 * enquanto) pra acompanhar a própria entrega — sem login, sem CSRF (só
 * leitura). Não tem {@code @PreAuthorize} de propósito: é a única tela do
 * módulo de entrega pensada pra alguém de fora da empresa acessar.
 */
@Controller
public class RastreioPublicoController {

    private final EntregaRotaService entregaRotaService;

    public RastreioPublicoController(EntregaRotaService entregaRotaService) {
        this.entregaRotaService = entregaRotaService;
    }

    @GetMapping("/rastreio/{token}")
    public String rastrear(@PathVariable UUID token, Model model) {
        try {
            model.addAttribute("rastreio", entregaRotaService.obterRastreioPublico(token));
        } catch (NoSuchElementException ex) {
            model.addAttribute("erro", "Link de rastreio inválido ou expirado.");
        }
        return "pages/rastreio";
    }
}
