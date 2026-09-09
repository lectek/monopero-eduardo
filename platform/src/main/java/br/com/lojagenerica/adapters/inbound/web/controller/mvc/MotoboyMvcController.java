package br.com.lojagenerica.adapters.inbound.web.controller.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Casca HTML das telas do motoboy — mesmo padrão das telas admin
 * (AdminMvcController): dado vem por fetch com o token JWT do localStorage.
 * Login é o mesmo /admin/login (mesma conta admin_users, mesmo endpoint de
 * autenticação) — só redireciona pra cá em vez de /admin/pedidos quando o
 * token tem role MOTOBOY (ver static/js/pages/admin/login.js).
 */
@Controller
@RequestMapping("/motoboy")
public class MotoboyMvcController {

    @GetMapping
    public String minhasRotas() {
        return "pages/motoboy/rotas";
    }

    @GetMapping("/rotas/{rotaId}")
    public String rotaDetalhe() {
        return "pages/motoboy/rota-detalhe";
    }
}
