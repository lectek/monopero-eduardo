package br.com.lojagenerica.adapters.inbound.web.controller.mvc;

import br.com.lojagenerica.application.catalogo.CatalogoService;
import br.com.lojagenerica.domain.catalogo.Produto;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private static final int MAX_DESTAQUES = 8;

    private final CatalogoService catalogoService;

    public HomeController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/")
    public String home(Model model) {
        List<Produto> vitrine = catalogoService.listarVitrine();
        model.addAttribute("produtosDestaque", vitrine.stream().limit(MAX_DESTAQUES).toList());
        return "pages/index";
    }
}
