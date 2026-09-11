package br.com.lojagenerica.gestao.web;

import br.com.lojagenerica.core.cadastro.UnidadeMedida;
import br.com.lojagenerica.core.cadastro.UnidadeMedidaRepository;
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

/** CRUD de unidade de medida — lista livre, nada seedado (ver docs/CONTEXTO.md). */
@Controller
@Validated
@PreAuthorize("hasAuthority('CADASTRO_GERENCIAR')")
public class UnidadeMedidaGestaoController {

    private final UnidadeMedidaRepository unidadeMedidaRepository;

    public UnidadeMedidaGestaoController(UnidadeMedidaRepository unidadeMedidaRepository) {
        this.unidadeMedidaRepository = unidadeMedidaRepository;
    }

    @GetMapping("/gestao/unidades-medida")
    public String listar(Model model) {
        model.addAttribute("unidades", unidadeMedidaRepository.findAll());
        return "pages/gestao/unidades-medida/lista";
    }

    @GetMapping("/gestao/unidades-medida/novo")
    public String novoForm(Model model) {
        model.addAttribute("unidade", new UnidadeMedida("", "", (short) 0, false));
        return "pages/gestao/unidades-medida/form";
    }

    @GetMapping("/gestao/unidades-medida/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        model.addAttribute("unidade", buscarOuFalhar(id));
        return "pages/gestao/unidades-medida/form";
    }

    @PostMapping("/gestao/unidades-medida")
    public String criar(@RequestParam @NotBlank String codigo, @RequestParam @NotBlank String descricao,
                         @RequestParam(defaultValue = "0") short casasDecimais,
                         @RequestParam(defaultValue = "false") boolean fracionavel) {
        unidadeMedidaRepository.save(new UnidadeMedida(codigo, descricao, casasDecimais, fracionavel));
        return "redirect:/gestao/unidades-medida";
    }

    @PostMapping("/gestao/unidades-medida/{id}")
    public String atualizar(@PathVariable Long id, @RequestParam @NotBlank String codigo,
                             @RequestParam @NotBlank String descricao,
                             @RequestParam(defaultValue = "0") short casasDecimais,
                             @RequestParam(defaultValue = "false") boolean fracionavel,
                             @RequestParam(defaultValue = "false") boolean ativo) {
        UnidadeMedida unidade = buscarOuFalhar(id);
        unidade.setCodigo(codigo);
        unidade.setDescricao(descricao);
        unidade.setCasasDecimais(casasDecimais);
        unidade.setFracionavel(fracionavel);
        unidade.setAtivo(ativo);
        unidadeMedidaRepository.save(unidade);
        return "redirect:/gestao/unidades-medida";
    }

    @PostMapping("/gestao/unidades-medida/{id}/alternar-ativo")
    public String alternarAtivo(@PathVariable Long id, @RequestParam boolean ativo) {
        UnidadeMedida unidade = buscarOuFalhar(id);
        unidade.setAtivo(ativo);
        unidadeMedidaRepository.save(unidade);
        return "redirect:/gestao/unidades-medida";
    }

    private UnidadeMedida buscarOuFalhar(Long id) {
        return unidadeMedidaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unidade de medida " + id + " não encontrada"));
    }
}
