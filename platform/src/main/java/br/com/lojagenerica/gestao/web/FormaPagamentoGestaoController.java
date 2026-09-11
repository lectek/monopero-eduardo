package br.com.lojagenerica.gestao.web;

import br.com.lojagenerica.core.cadastro.FormaPagamento;
import br.com.lojagenerica.core.cadastro.FormaPagamentoRepository;
import br.com.lojagenerica.core.cadastro.NaturezaFormaPagamento;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** CRUD de forma de pagamento — o usuário cria os métodos, natureza só classifica se afeta caixa físico. */
@Controller
@PreAuthorize("hasAuthority('CADASTRO_GERENCIAR')")
public class FormaPagamentoGestaoController {

    private final FormaPagamentoRepository formaPagamentoRepository;

    public FormaPagamentoGestaoController(FormaPagamentoRepository formaPagamentoRepository) {
        this.formaPagamentoRepository = formaPagamentoRepository;
    }

    @GetMapping("/gestao/formas-pagamento")
    public String listar(Model model) {
        model.addAttribute("formas", formaPagamentoRepository.findAll());
        return "pages/gestao/formas-pagamento/lista";
    }

    @GetMapping("/gestao/formas-pagamento/novo")
    public String novoForm(Model model) {
        model.addAttribute("forma", new FormaPagamento("", NaturezaFormaPagamento.DINHEIRO, true));
        model.addAttribute("naturezas", NaturezaFormaPagamento.values());
        return "pages/gestao/formas-pagamento/form";
    }

    @GetMapping("/gestao/formas-pagamento/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        model.addAttribute("forma", buscarOuFalhar(id));
        model.addAttribute("naturezas", NaturezaFormaPagamento.values());
        return "pages/gestao/formas-pagamento/form";
    }

    @PostMapping("/gestao/formas-pagamento")
    public String criar(@RequestParam String nome, @RequestParam NaturezaFormaPagamento natureza,
                         @RequestParam(defaultValue = "true") boolean afetaCaixa,
                         @RequestParam(defaultValue = "false") boolean permiteParcelamento,
                         @RequestParam(required = false) Short maxParcelas,
                         @RequestParam(required = false) Integer prazoRecebimentoDias,
                         @RequestParam(required = false) BigDecimal taxaPercentual) {
        FormaPagamento forma = new FormaPagamento(nome, natureza, afetaCaixa);
        forma.setPermiteParcelamento(permiteParcelamento);
        forma.setMaxParcelas(maxParcelas);
        forma.setPrazoRecebimentoDias(prazoRecebimentoDias);
        forma.setTaxaPercentual(taxaPercentual);
        formaPagamentoRepository.save(forma);
        return "redirect:/gestao/formas-pagamento";
    }

    @PostMapping("/gestao/formas-pagamento/{id}")
    public String atualizar(@PathVariable Long id, @RequestParam String nome, @RequestParam NaturezaFormaPagamento natureza,
                             @RequestParam(defaultValue = "true") boolean afetaCaixa,
                             @RequestParam(defaultValue = "false") boolean permiteParcelamento,
                             @RequestParam(required = false) Short maxParcelas,
                             @RequestParam(required = false) Integer prazoRecebimentoDias,
                             @RequestParam(required = false) BigDecimal taxaPercentual,
                             @RequestParam(defaultValue = "false") boolean ativo) {
        FormaPagamento forma = buscarOuFalhar(id);
        forma.setNome(nome);
        forma.setNatureza(natureza);
        forma.setAfetaCaixa(afetaCaixa);
        forma.setPermiteParcelamento(permiteParcelamento);
        forma.setMaxParcelas(maxParcelas);
        forma.setPrazoRecebimentoDias(prazoRecebimentoDias);
        forma.setTaxaPercentual(taxaPercentual);
        forma.setAtivo(ativo);
        formaPagamentoRepository.save(forma);
        return "redirect:/gestao/formas-pagamento";
    }

    @PostMapping("/gestao/formas-pagamento/{id}/alternar-ativo")
    public String alternarAtivo(@PathVariable Long id, @RequestParam boolean ativo) {
        FormaPagamento forma = buscarOuFalhar(id);
        forma.setAtivo(ativo);
        formaPagamentoRepository.save(forma);
        return "redirect:/gestao/formas-pagamento";
    }

    private FormaPagamento buscarOuFalhar(Long id) {
        return formaPagamentoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Forma de pagamento " + id + " não encontrada"));
    }
}
