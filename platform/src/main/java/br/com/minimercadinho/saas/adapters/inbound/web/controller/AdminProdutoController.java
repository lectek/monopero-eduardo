package br.com.minimercadinho.saas.adapters.inbound.web.controller;

import br.com.minimercadinho.saas.adapters.outbound.ims.ImsProdutoRepository;
import br.com.minimercadinho.saas.domain.catalogo.Produto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint mínimo pra validar o gate do {@link br.com.minimercadinho.saas.adapters.inbound.web.security.AdminJwtAuthFilter}. */
@RestController
public class AdminProdutoController {

    private final ImsProdutoRepository imsProdutoRepository;

    public AdminProdutoController(ImsProdutoRepository imsProdutoRepository) {
        this.imsProdutoRepository = imsProdutoRepository;
    }

    @GetMapping("/api/admin/produtos")
    public List<Produto> listarTodos() {
        return imsProdutoRepository.fetchTodos();
    }
}
