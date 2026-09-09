package br.com.lojagenerica.adapters.inbound.web.controller.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Só a casca HTML — mesma abordagem das outras telas admin (ver AdminMvcController). */
@Controller
@RequestMapping("/admin/entregas")
public class AdminEntregasMvcController {

    @GetMapping
    public String entregas() {
        return "pages/admin/entregas";
    }

    @GetMapping("/rotas/{rotaId}")
    public String rotaDetalhe() {
        return "pages/admin/entrega-detalhe";
    }
}
