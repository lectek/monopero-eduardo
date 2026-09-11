package br.com.lojagenerica.gestao.web;

import br.com.lojagenerica.core.cadastro.Marca;
import br.com.lojagenerica.core.cadastro.MarcaRepository;
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
 * CRUD de marca — cadastro livre, sem valor comercial hardcoded (ver
 * docs/CONTEXTO.md). Sem objeto de formulário/binding: os campos são
 * poucos e simples o bastante pra ler direto via {@code @RequestParam},
 * evitando as pegadinhas de {@code th:field} com checkbox/record.
 */
@Controller
@Validated
@PreAuthorize("hasAuthority('CADASTRO_GERENCIAR')")
public class MarcaGestaoController {

    private final MarcaRepository marcaRepository;

    public MarcaGestaoController(MarcaRepository marcaRepository) {
        this.marcaRepository = marcaRepository;
    }

    @GetMapping("/gestao/marcas")
    public String listar(Model model) {
        model.addAttribute("marcas", marcaRepository.findAll());
        return "pages/gestao/marcas/lista";
    }

    @GetMapping("/gestao/marcas/novo")
    public String novoForm(Model model) {
        model.addAttribute("marca", new Marca("", null));
        return "pages/gestao/marcas/form";
    }

    @GetMapping("/gestao/marcas/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        model.addAttribute("marca", buscarOuFalhar(id));
        return "pages/gestao/marcas/form";
    }

    @PostMapping("/gestao/marcas")
    public String criar(@RequestParam @NotBlank String nome, @RequestParam(required = false) String fabricante) {
        marcaRepository.save(new Marca(nome, fabricante));
        return "redirect:/gestao/marcas";
    }

    @PostMapping("/gestao/marcas/{id}")
    public String atualizar(@PathVariable Long id, @RequestParam @NotBlank String nome,
                             @RequestParam(required = false) String fabricante,
                             @RequestParam(defaultValue = "false") boolean ativo) {
        Marca marca = buscarOuFalhar(id);
        marca.setNome(nome);
        marca.setFabricante(fabricante);
        marca.setAtivo(ativo);
        marcaRepository.save(marca);
        return "redirect:/gestao/marcas";
    }

    @PostMapping("/gestao/marcas/{id}/alternar-ativo")
    public String alternarAtivo(@PathVariable Long id, @RequestParam boolean ativo) {
        Marca marca = buscarOuFalhar(id);
        marca.setAtivo(ativo);
        marcaRepository.save(marca);
        return "redirect:/gestao/marcas";
    }

    private Marca buscarOuFalhar(Long id) {
        return marcaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Marca " + id + " não encontrada"));
    }
}
