package br.com.minimercadinho.saas.adapters.inbound.web.controller.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Páginas cujo conteúdo é montado no navegador (fetch pras APIs JSON já
 * existentes) — o controller só entrega a casca HTML.
 */
@Controller
public class StorefrontController {

    @GetMapping("/produtos")
    public String produtos() {
        return "pages/produtos";
    }

    @GetMapping("/carrinho")
    public String carrinho() {
        return "pages/carrinho";
    }

    @GetMapping("/checkout")
    public String checkout() {
        return "pages/checkout";
    }

    @GetMapping("/politica-de-privacidade")
    public String politicaDePrivacidade() {
        return "pages/legal/privacidade";
    }

    @GetMapping("/termos-de-servico")
    public String termosDeServico() {
        return "pages/legal/termos";
    }
}
