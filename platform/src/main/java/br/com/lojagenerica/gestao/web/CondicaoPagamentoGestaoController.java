package br.com.lojagenerica.gestao.web;

import br.com.lojagenerica.core.cadastro.CondicaoPagamento;
import br.com.lojagenerica.core.cadastro.CondicaoPagamentoRepository;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

/** CRUD de condição de pagamento — gera as parcelas de conta a pagar na confirmação da compra (Fase E). */
@Controller
@Validated
@PreAuthorize("hasAuthority('CADASTRO_GERENCIAR')")
public class CondicaoPagamentoGestaoController {

    private final CondicaoPagamentoRepository condicaoPagamentoRepository;

    public CondicaoPagamentoGestaoController(CondicaoPagamentoRepository condicaoPagamentoRepository) {
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
    }

    @GetMapping("/gestao/condicoes-pagamento")
    public String listar(Model model) {
        model.addAttribute("condicoes", condicaoPagamentoRepository.findAll());
        return "pages/gestao/condicoes-pagamento/lista";
    }

    @GetMapping("/gestao/condicoes-pagamento/novo")
    public String novoForm(Model model) {
        model.addAttribute("condicao", new CondicaoPagamento("", (short) 1, 0));
        return "pages/gestao/condicoes-pagamento/form";
    }

    @GetMapping("/gestao/condicoes-pagamento/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        model.addAttribute("condicao", buscarOuFalhar(id));
        return "pages/gestao/condicoes-pagamento/form";
    }

    @PostMapping("/gestao/condicoes-pagamento")
    public String criar(@RequestParam @NotBlank String nome, @RequestParam @Min(1) short parcelas,
                         @RequestParam @Min(0) int intervaloDias,
                         @RequestParam(required = false) BigDecimal entradaPercentual) {
        CondicaoPagamento condicao = new CondicaoPagamento(nome, parcelas, intervaloDias);
        condicao.setEntradaPercentual(entradaPercentual);
        condicaoPagamentoRepository.save(condicao);
        return "redirect:/gestao/condicoes-pagamento";
    }

    @PostMapping("/gestao/condicoes-pagamento/{id}")
    public String atualizar(@PathVariable Long id, @RequestParam @NotBlank String nome,
                             @RequestParam @Min(1) short parcelas,
                             @RequestParam @Min(0) int intervaloDias,
                             @RequestParam(required = false) BigDecimal entradaPercentual,
                             @RequestParam(defaultValue = "false") boolean ativo) {
        CondicaoPagamento condicao = buscarOuFalhar(id);
        condicao.setNome(nome);
        condicao.setParcelas(parcelas);
        condicao.setIntervaloDias(intervaloDias);
        condicao.setEntradaPercentual(entradaPercentual);
        condicao.setAtivo(ativo);
        condicaoPagamentoRepository.save(condicao);
        return "redirect:/gestao/condicoes-pagamento";
    }

    @PostMapping("/gestao/condicoes-pagamento/{id}/alternar-ativo")
    public String alternarAtivo(@PathVariable Long id, @RequestParam boolean ativo) {
        CondicaoPagamento condicao = buscarOuFalhar(id);
        condicao.setAtivo(ativo);
        condicaoPagamentoRepository.save(condicao);
        return "redirect:/gestao/condicoes-pagamento";
    }

    private CondicaoPagamento buscarOuFalhar(Long id) {
        return condicaoPagamentoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Condição de pagamento " + id + " não encontrada"));
    }
}
