package br.com.lojagenerica.gestao.web;

import br.com.lojagenerica.core.cadastro.LocalEstoque;
import br.com.lojagenerica.core.cadastro.LocalEstoqueRepository;
import jakarta.validation.constraints.NotBlank;
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
 * CRUD de local de estoque — o PDV (Fase D) sincroniza esta lista pra
 * deixar o caixa escolher de onde a venda sai (ver
 * br.com.lojagenerica.pdv.PdvSyncService).
 */
@Controller
@Validated
@PreAuthorize("hasAuthority('CADASTRO_GERENCIAR')")
public class LocalEstoqueGestaoController {

    private final LocalEstoqueRepository localEstoqueRepository;

    public LocalEstoqueGestaoController(LocalEstoqueRepository localEstoqueRepository) {
        this.localEstoqueRepository = localEstoqueRepository;
    }

    @GetMapping("/gestao/locais-estoque")
    public String listar(Model model) {
        model.addAttribute("locais", localEstoqueRepository.findAll());
        return "pages/gestao/locais-estoque/lista";
    }

    @GetMapping("/gestao/locais-estoque/novo")
    public String novoForm(Model model) {
        model.addAttribute("local", new LocalEstoque("", "", false));
        return "pages/gestao/locais-estoque/form";
    }

    @GetMapping("/gestao/locais-estoque/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        model.addAttribute("local", buscarOuFalhar(id));
        return "pages/gestao/locais-estoque/form";
    }

    @PostMapping("/gestao/locais-estoque")
    public String criar(@RequestParam @NotBlank String nome, @RequestParam(required = false) String tipo,
                         @RequestParam(defaultValue = "false") boolean principal) {
        localEstoqueRepository.save(new LocalEstoque(nome, tipo, principal));
        return "redirect:/gestao/locais-estoque";
    }

    @PostMapping("/gestao/locais-estoque/{id}")
    public String atualizar(@PathVariable Long id, @RequestParam @NotBlank String nome,
                             @RequestParam(required = false) String tipo,
                             @RequestParam(defaultValue = "false") boolean principal,
                             @RequestParam(defaultValue = "false") boolean ativo) {
        LocalEstoque local = buscarOuFalhar(id);
        local.setNome(nome);
        local.setTipo(tipo);
        local.setPrincipal(principal);
        local.setAtivo(ativo);
        localEstoqueRepository.save(local);
        return "redirect:/gestao/locais-estoque";
    }

    @PostMapping("/gestao/locais-estoque/{id}/alternar-ativo")
    public String alternarAtivo(@PathVariable Long id, @RequestParam boolean ativo) {
        LocalEstoque local = buscarOuFalhar(id);
        local.setAtivo(ativo);
        localEstoqueRepository.save(local);
        return "redirect:/gestao/locais-estoque";
    }

    private LocalEstoque buscarOuFalhar(Long id) {
        return localEstoqueRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Local de estoque " + id + " não encontrado"));
    }
}
