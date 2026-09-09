package br.com.minimercadinho.saas.adapters.inbound.web.controller;

import br.com.minimercadinho.saas.application.catalogo.CatalogoService;
import br.com.minimercadinho.saas.domain.catalogo.Produto;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicProdutoController {

    private final CatalogoService catalogoService;

    public PublicProdutoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/api/public/produtos")
    public ResponseEntity<List<ProdutoResponse>> listar() {
        List<ProdutoResponse> produtos = catalogoService.listarVitrine().stream()
                .map(ProdutoResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(produtos);
    }

    public record ProdutoResponse(
            String nome,
            String cor,
            String peso,
            int quantidadeDisponivel,
            String codigoBarras,
            String descricao,
            double preco
    ) {
        static ProdutoResponse from(Produto p) {
            return new ProdutoResponse(p.nome(), p.cor(), p.peso(), p.quantidade(), p.codigoBarras(), p.descricao(), p.preco());
        }
    }
}
