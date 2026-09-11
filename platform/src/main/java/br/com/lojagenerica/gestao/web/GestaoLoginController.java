package br.com.lojagenerica.gestao.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** A página de login em si — o processamento do POST é todo do Spring Security (ver AdminSecurityConfig). */
@Controller
public class GestaoLoginController {

    @GetMapping("/gestao/login")
    public String login() {
        return "pages/gestao/login";
    }
}
