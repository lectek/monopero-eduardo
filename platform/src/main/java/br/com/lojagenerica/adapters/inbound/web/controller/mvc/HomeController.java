package br.com.lojagenerica.adapters.inbound.web.controller.mvc;

import br.com.lojagenerica.core.produto.Produto;
import br.com.lojagenerica.core.produto.ProdutoRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Reescrito na Fase C: a vitrine agora lê {@code core.produto.Produto}
 * (genérico) em vez do catálogo antigo do IMS (chave nome+cor+peso). Só
 * produtos ATIVO com preço cadastrado aparecem — o mesmo critério que a
 * vitrine antiga usava ("disponível na vitrine").
 */
@Controller
public class HomeController {

    private static final int MAX_DESTAQUES = 8;

    private final ProdutoRepository produtoRepository;

    public HomeController(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    @GetMapping("/")
    public String home(Model model) {
        List<Produto> vitrine = produtoRepository.findAll(PageRequest.of(0, MAX_DESTAQUES)).stream()
                .filter(p -> p.getStatus() == Produto.StatusProduto.ATIVO)
                .filter(p -> p.getPrecoVenda() != null && p.getPrecoVenda().signum() > 0)
                .toList();
        model.addAttribute("produtosDestaque", vitrine);
        return "pages/index";
    }
}
