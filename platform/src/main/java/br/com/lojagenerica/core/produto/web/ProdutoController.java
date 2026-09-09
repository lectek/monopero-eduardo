package br.com.lojagenerica.core.produto.web;

import br.com.lojagenerica.core.produto.Produto;
import br.com.lojagenerica.core.produto.ProdutoRepository;
import br.com.lojagenerica.core.produto.ProdutoService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/produtos")
public class ProdutoController {

    private final ProdutoRepository produtoRepository;
    private final ProdutoService produtoService;

    public ProdutoController(ProdutoRepository produtoRepository, ProdutoService produtoService) {
        this.produtoRepository = produtoRepository;
        this.produtoService = produtoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUTO_LER')")
    public List<ProdutoResponse> listar() {
        return produtoRepository.findAll().stream().map(ProdutoResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUTO_LER')")
    public ProdutoResponse buscar(@PathVariable Long id) {
        return produtoRepository.findById(id).map(ProdutoResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUTO_ESCREVER')")
    public ResponseEntity<ProdutoResponse> criar(@RequestBody CriarProdutoRequest request) {
        Produto produto = produtoService.criar(request.nome(), request.unidadeEstoqueId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProdutoResponse.from(produto));
    }

    @PatchMapping("/{id}/preco")
    @PreAuthorize("hasAuthority('PRODUTO_ALTERAR_PRECO')")
    public ProdutoResponse alterarPreco(@PathVariable Long id, @RequestBody AlterarPrecoRequest request) {
        Produto produto = produtoService.alterarPreco(id, request.precoVenda(), request.custoAquisicao(), request.motivo());
        return ProdutoResponse.from(produto);
    }

    public record CriarProdutoRequest(@NotBlank String nome, @NotNull Long unidadeEstoqueId) {
    }

    public record AlterarPrecoRequest(BigDecimal precoVenda, BigDecimal custoAquisicao, @NotBlank String motivo) {
    }

    public record ProdutoResponse(Long id, String nome, String codigoInterno, BigDecimal precoVenda,
                                   BigDecimal custoAquisicao, String status) {
        static ProdutoResponse from(Produto p) {
            return new ProdutoResponse(p.getId(), p.getNome(), p.getCodigoInterno(), p.getPrecoVenda(),
                    p.getCustoAquisicao(), p.getStatus().name());
        }
    }
}
