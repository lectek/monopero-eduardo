package br.com.lojagenerica.gestao.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class GestaoHomeController {

    @GetMapping("/gestao")
    public String home() {
        return "pages/gestao/home";
    }
}
