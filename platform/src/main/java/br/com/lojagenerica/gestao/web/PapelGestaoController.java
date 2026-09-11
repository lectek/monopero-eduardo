package br.com.lojagenerica.gestao.web;

import br.com.lojagenerica.core.acesso.Papel;
import br.com.lojagenerica.core.acesso.PapelRepository;
import br.com.lojagenerica.core.acesso.PermissaoCatalogo;
import br.com.lojagenerica.core.acesso.PermissaoRepository;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * CRUD de papel — ADMINISTRADOR ({@code sistema=true}) nunca aparece
 * editável aqui: sempre tem todas as permissões, sincronizado no boot
 * (ver {@code PermissaoCatalogSyncService}), e um tenant não pode se
 * autobloquear removendo a própria permissão de gerenciar usuários.
 */
@Controller
@Validated
@PreAuthorize("hasAuthority('USUARIO_GERENCIAR')")
public class PapelGestaoController {

    private final PapelRepository papelRepository;
    private final PermissaoRepository permissaoRepository;

    public PapelGestaoController(PapelRepository papelRepository, PermissaoRepository permissaoRepository) {
        this.papelRepository = papelRepository;
        this.permissaoRepository = permissaoRepository;
    }

    @GetMapping("/gestao/papeis")
    public String listar(Model model) {
        model.addAttribute("papeis", papelRepository.findAllComPermissoes());
        return "pages/gestao/papeis/lista";
    }

    @GetMapping("/gestao/papeis/novo")
    public String novoForm(Model model) {
        model.addAttribute("papel", null);
        model.addAttribute("catalogo", PermissaoCatalogo.values());
        model.addAttribute("codigosSelecionados", Set.of());
        return "pages/gestao/papeis/form";
    }

    @GetMapping("/gestao/papeis/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        Papel papel = buscarComPermissoesOuFalhar(id);
        if (papel.isSistema()) {
            return "redirect:/gestao/papeis";
        }
        model.addAttribute("papel", papel);
        model.addAttribute("catalogo", PermissaoCatalogo.values());
        model.addAttribute("codigosSelecionados",
                papel.getPermissoes().stream().map(p -> p.getCodigo()).collect(Collectors.toSet()));
        return "pages/gestao/papeis/form";
    }

    @PostMapping("/gestao/papeis")
    public String criar(@RequestParam @NotBlank String nome, @RequestParam(required = false) String descricao,
                         @RequestParam(required = false) List<String> permissoes) {
        Papel papel = new Papel(nome, descricao, false);
        aplicarPermissoes(papel, permissoes);
        papelRepository.save(papel);
        return "redirect:/gestao/papeis";
    }

    @PostMapping("/gestao/papeis/{id}")
    public String atualizar(@PathVariable Long id, @RequestParam @NotBlank String nome,
                             @RequestParam(required = false) String descricao,
                             @RequestParam(required = false) List<String> permissoes) {
        Papel papel = buscarComPermissoesOuFalhar(id);
        if (papel.isSistema()) {
            return "redirect:/gestao/papeis";
        }
        papel.setNome(nome);
        papel.setDescricao(descricao);
        aplicarPermissoes(papel, permissoes);
        papelRepository.save(papel);
        return "redirect:/gestao/papeis";
    }

    private void aplicarPermissoes(Papel papel, List<String> codigos) {
        papel.getPermissoes().clear();
        if (codigos == null) {
            return;
        }
        for (String codigo : codigos) {
            permissaoRepository.findByCodigo(codigo).ifPresent(papel.getPermissoes()::add);
        }
    }

    private Papel buscarComPermissoesOuFalhar(Long id) {
        return papelRepository.findByIdComPermissoes(id)
                .orElseThrow(() -> new NoSuchElementException("Papel " + id + " não encontrado"));
    }
}
