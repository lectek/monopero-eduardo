package br.com.lojagenerica.gestao.web;

import br.com.lojagenerica.core.cadastro.SentidoMovimentacao;
import br.com.lojagenerica.core.cadastro.TipoMovimentacao;
import br.com.lojagenerica.core.cadastro.TipoMovimentacaoRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * CRUD de tipo de movimentação — linhas {@code sistema=true} (VENDA,
 * COMPRA, DEVOLUCAO_*, INVENTARIO, TRANSFERENCIA_*) são criadas pelo
 * provisionamento e nunca editáveis/desativáveis aqui (o código referencia
 * esses códigos diretamente); só as linhas livres do usuário (PERDA,
 * QUEBRA, DOACAO...) passam por esta tela.
 */
@Controller
@Validated
@PreAuthorize("hasAuthority('CADASTRO_GERENCIAR')")
public class TipoMovimentacaoGestaoController {

    private final TipoMovimentacaoRepository tipoMovimentacaoRepository;

    public TipoMovimentacaoGestaoController(TipoMovimentacaoRepository tipoMovimentacaoRepository) {
        this.tipoMovimentacaoRepository = tipoMovimentacaoRepository;
    }

    @GetMapping("/gestao/tipos-movimentacao")
    public String listar(Model model) {
        model.addAttribute("tipos", tipoMovimentacaoRepository.findAll());
        return "pages/gestao/tipos-movimentacao/lista";
    }

    @GetMapping("/gestao/tipos-movimentacao/novo")
    public String novoForm(Model model) {
        model.addAttribute("tipo", new TipoMovimentacao("", "", SentidoMovimentacao.SAIDA, false, true, false));
        model.addAttribute("sentidos", SentidoMovimentacao.values());
        return "pages/gestao/tipos-movimentacao/form";
    }

    @GetMapping("/gestao/tipos-movimentacao/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        TipoMovimentacao tipo = buscarOuFalhar(id);
        if (tipo.isSistema()) {
            return "redirect:/gestao/tipos-movimentacao";
        }
        model.addAttribute("tipo", tipo);
        model.addAttribute("sentidos", SentidoMovimentacao.values());
        return "pages/gestao/tipos-movimentacao/form";
    }

    @PostMapping("/gestao/tipos-movimentacao")
    public String criar(@RequestParam @NotBlank String codigo, @RequestParam @NotBlank String nome,
                         @RequestParam @NotNull SentidoMovimentacao sentido,
                         @RequestParam(defaultValue = "false") boolean exigeMotivo,
                         @RequestParam(defaultValue = "false") boolean afetaCustoMedio) {
        tipoMovimentacaoRepository.save(new TipoMovimentacao(codigo, nome, sentido, false, exigeMotivo, afetaCustoMedio));
        return "redirect:/gestao/tipos-movimentacao";
    }

    @PostMapping("/gestao/tipos-movimentacao/{id}")
    public String atualizar(@PathVariable Long id, @RequestParam @NotBlank String nome,
                             @RequestParam(defaultValue = "false") boolean exigeMotivo,
                             @RequestParam(defaultValue = "false") boolean afetaCustoMedio,
                             @RequestParam(defaultValue = "false") boolean ativo) {
        TipoMovimentacao tipo = buscarOuFalhar(id);
        if (tipo.isSistema()) {
            return "redirect:/gestao/tipos-movimentacao";
        }
        tipo.setNome(nome);
        tipo.setExigeMotivo(exigeMotivo);
        tipo.setAfetaCustoMedio(afetaCustoMedio);
        tipo.setAtivo(ativo);
        tipoMovimentacaoRepository.save(tipo);
        return "redirect:/gestao/tipos-movimentacao";
    }

    @PostMapping("/gestao/tipos-movimentacao/{id}/alternar-ativo")
    public String alternarAtivo(@PathVariable Long id, @RequestParam boolean ativo) {
        TipoMovimentacao tipo = buscarOuFalhar(id);
        if (tipo.isSistema()) {
            return "redirect:/gestao/tipos-movimentacao";
        }
        tipo.setAtivo(ativo);
        tipoMovimentacaoRepository.save(tipo);
        return "redirect:/gestao/tipos-movimentacao";
    }

    private TipoMovimentacao buscarOuFalhar(Long id) {
        return tipoMovimentacaoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Tipo de movimentação " + id + " não encontrado"));
    }
}
