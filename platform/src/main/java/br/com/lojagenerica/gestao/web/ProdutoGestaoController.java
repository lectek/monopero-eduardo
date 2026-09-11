package br.com.lojagenerica.gestao.web;

import br.com.lojagenerica.core.cadastro.CategoriaRepository;
import br.com.lojagenerica.core.cadastro.LocalEstoqueRepository;
import br.com.lojagenerica.core.cadastro.MarcaRepository;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
import br.com.lojagenerica.core.produto.Produto;
import br.com.lojagenerica.core.produto.Produto.StatusProduto;
import br.com.lojagenerica.core.produto.ProdutoRepository;
import br.com.lojagenerica.core.produto.ProdutoService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * CRUD de produto — o cadastro mais usado no dia a dia. Preço/custo NUNCA
 * são setados direto aqui: passam por {@link ProdutoService#alterarPreco},
 * o único caminho auditado (grava {@code produto_preco_historico} +
 * evento de auditoria) — só chamado quando o valor submetido realmente
 * difere do atual, pra não poluir o histórico com "alterações" de
 * R$10,00 pra R$10,00 toda vez que alguém salva uma edição de descrição.
 */
@Controller
@Validated
@PreAuthorize("hasAuthority('PRODUTO_LER')")
public class ProdutoGestaoController {

    private final ProdutoRepository produtoRepository;
    private final ProdutoService produtoService;
    private final UnidadeMedidaRepository unidadeMedidaRepository;
    private final CategoriaRepository categoriaRepository;
    private final MarcaRepository marcaRepository;
    private final LocalEstoqueRepository localEstoqueRepository;

    public ProdutoGestaoController(ProdutoRepository produtoRepository, ProdutoService produtoService,
                                    UnidadeMedidaRepository unidadeMedidaRepository, CategoriaRepository categoriaRepository,
                                    MarcaRepository marcaRepository, LocalEstoqueRepository localEstoqueRepository) {
        this.produtoRepository = produtoRepository;
        this.produtoService = produtoService;
        this.unidadeMedidaRepository = unidadeMedidaRepository;
        this.categoriaRepository = categoriaRepository;
        this.marcaRepository = marcaRepository;
        this.localEstoqueRepository = localEstoqueRepository;
    }

    @GetMapping("/gestao/produtos")
    public String listar(Model model) {
        model.addAttribute("produtos", produtoRepository.findAll());
        return "pages/gestao/produtos/lista";
    }

    @GetMapping("/gestao/produtos/novo")
    @PreAuthorize("hasAuthority('PRODUTO_ESCREVER')")
    public String novoForm(Model model) {
        model.addAttribute("produto", null);
        carregarListasDeApoio(model);
        return "pages/gestao/produtos/form";
    }

    @GetMapping("/gestao/produtos/{id}/editar")
    @PreAuthorize("hasAuthority('PRODUTO_ESCREVER')")
    public String editarForm(@PathVariable Long id, Model model) {
        model.addAttribute("produto", buscarOuFalhar(id));
        carregarListasDeApoio(model);
        return "pages/gestao/produtos/form";
    }

    @PostMapping("/gestao/produtos")
    @PreAuthorize("hasAuthority('PRODUTO_ESCREVER')")
    public String criar(@RequestParam @NotBlank String nome, @RequestParam @NotNull Long unidadeEstoqueId,
                         @RequestParam(required = false) String codigoInterno,
                         @RequestParam(required = false) String descricao,
                         @RequestParam(required = false) BigDecimal precoVenda,
                         @RequestParam(required = false) BigDecimal custoAquisicao,
                         @RequestParam(required = false) Long categoriaId,
                         @RequestParam(required = false) Long marcaId,
                         @RequestParam(required = false) String fabricante,
                         @RequestParam(required = false) Long localEstoquePadraoId,
                         @RequestParam(defaultValue = "true") boolean controlaEstoque,
                         @RequestParam(defaultValue = "false") boolean permiteVendaSemEstoque,
                         @RequestParam(required = false) BigDecimal estoqueMinimo,
                         @RequestParam(required = false) BigDecimal estoqueMaximo) {
        Produto produto = produtoService.criarComDadosIniciais(nome, unidadeEstoqueId, codigoInterno, descricao, precoVenda);
        if (custoAquisicao != null) {
            // criarComDadosIniciais só aceita preço (não existe "custo anterior" num produto recém-nascido) —
            // repassa o mesmo precoVenda pra não zerá-lo ao gravar o custo junto.
            produtoService.alterarPreco(produto.getId(), precoVenda, custoAquisicao, "Custo inicial via painel de gestão");
            produto = buscarOuFalhar(produto.getId());
        }
        aplicarCamposLivres(produto, categoriaId, marcaId, fabricante, localEstoquePadraoId,
                controlaEstoque, permiteVendaSemEstoque, estoqueMinimo, estoqueMaximo);
        produtoRepository.save(produto);
        return "redirect:/gestao/produtos";
    }

    @PostMapping("/gestao/produtos/{id}")
    @PreAuthorize("hasAuthority('PRODUTO_ESCREVER')")
    public String atualizar(@PathVariable Long id, @RequestParam @NotBlank String nome,
                             @RequestParam @NotNull Long unidadeEstoqueId,
                             @RequestParam(required = false) String codigoInterno,
                             @RequestParam(required = false) String descricao,
                             @RequestParam(required = false) BigDecimal precoVenda,
                             @RequestParam(required = false) BigDecimal custoAquisicao,
                             @RequestParam(required = false) String motivoAlteracaoPreco,
                             @RequestParam(required = false) Long categoriaId,
                             @RequestParam(required = false) Long marcaId,
                             @RequestParam(required = false) String fabricante,
                             @RequestParam(required = false) Long localEstoquePadraoId,
                             @RequestParam(defaultValue = "true") boolean controlaEstoque,
                             @RequestParam(defaultValue = "false") boolean permiteVendaSemEstoque,
                             @RequestParam(required = false) BigDecimal estoqueMinimo,
                             @RequestParam(required = false) BigDecimal estoqueMaximo,
                             @RequestParam StatusProduto status) {
        Produto produto = buscarOuFalhar(id);

        boolean precoMudou = diferente(produto.getPrecoVenda(), precoVenda) || diferente(produto.getCustoAquisicao(), custoAquisicao);
        if (precoMudou) {
            String motivo = (motivoAlteracaoPreco == null || motivoAlteracaoPreco.isBlank())
                    ? "Alteração via painel de gestão" : motivoAlteracaoPreco;
            produtoService.alterarPreco(id, precoVenda, custoAquisicao, motivo);
            produto = buscarOuFalhar(id);
        }

        produto.setNome(nome);
        var unidade = unidadeMedidaRepository.findById(unidadeEstoqueId)
                .orElseThrow(() -> new NoSuchElementException("Unidade de medida " + unidadeEstoqueId + " não encontrada"));
        produto.setUnidadeEstoque(unidade);
        produto.setCodigoInterno(codigoInterno);
        produto.setDescricao(descricao);
        produto.setStatus(status);
        aplicarCamposLivres(produto, categoriaId, marcaId, fabricante, localEstoquePadraoId,
                controlaEstoque, permiteVendaSemEstoque, estoqueMinimo, estoqueMaximo);
        produtoRepository.save(produto);
        return "redirect:/gestao/produtos";
    }

    private void aplicarCamposLivres(Produto produto, Long categoriaId, Long marcaId, String fabricante,
                                      Long localEstoquePadraoId, boolean controlaEstoque, boolean permiteVendaSemEstoque,
                                      BigDecimal estoqueMinimo, BigDecimal estoqueMaximo) {
        produto.setCategoria(categoriaId == null ? null : categoriaRepository.findById(categoriaId).orElse(null));
        produto.setMarca(marcaId == null ? null : marcaRepository.findById(marcaId).orElse(null));
        produto.setFabricante(fabricante);
        produto.setLocalEstoquePadrao(localEstoquePadraoId == null ? null : localEstoqueRepository.findById(localEstoquePadraoId).orElse(null));
        produto.setControlaEstoque(controlaEstoque);
        produto.setPermiteVendaSemEstoque(permiteVendaSemEstoque);
        produto.setEstoqueMinimo(estoqueMinimo);
        produto.setEstoqueMaximo(estoqueMaximo);
    }

    private boolean diferente(BigDecimal atual, BigDecimal novo) {
        if (atual == null && novo == null) {
            return false;
        }
        if (atual == null || novo == null) {
            return true;
        }
        return atual.compareTo(novo) != 0;
    }

    private void carregarListasDeApoio(Model model) {
        model.addAttribute("unidades", unidadeMedidaRepository.findAll());
        model.addAttribute("categorias", categoriaRepository.findAll());
        model.addAttribute("marcas", marcaRepository.findAll());
        model.addAttribute("locaisEstoque", localEstoqueRepository.findAll());
        model.addAttribute("statusValores", StatusProduto.values());
    }

    private Produto buscarOuFalhar(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Produto " + id + " não encontrado"));
    }
}
