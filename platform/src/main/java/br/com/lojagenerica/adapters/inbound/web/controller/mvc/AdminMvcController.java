package br.com.lojagenerica.adapters.inbound.web.controller.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Só a casca HTML das telas do painel admin — dados vêm por fetch (JS) pras
 * mesmas APIs REST protegidas pelo {@code AdminJwtAuthFilter}, com o token
 * guardado no localStorage do navegador (ver {@code static/js/admin/auth.js}).
 * Nenhuma dessas rotas carrega dado sensível no HTML renderizado no servidor.
 */
@Controller
@RequestMapping("/admin")
public class AdminMvcController {

    @GetMapping("/login")
    public String login() {
        return "pages/admin/login";
    }

    @GetMapping({"", "/pedidos"})
    public String pedidos() {
        return "pages/admin/pedidos";
    }

    @GetMapping("/produtos")
    public String produtos() {
        return "pages/admin/produtos";
    }
}
