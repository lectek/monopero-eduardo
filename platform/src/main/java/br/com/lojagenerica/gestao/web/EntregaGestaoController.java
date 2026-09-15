package br.com.lojagenerica.gestao.web;

import br.com.lojagenerica.application.service.delivery.DeliveryRouteService;
import br.com.lojagenerica.core.entrega.EntregaRota;
import br.com.lojagenerica.core.entrega.EntregaRotaService;
import br.com.lojagenerica.security.admin.AdminPrincipal;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Roteirização de vendas em modo ENTREGA — seleciona vendas elegíveis,
 * pré-visualiza a rota calculada por {@link DeliveryRouteService} (TSP
 * exato, reaproveitado sem reescrever), cria a rota, acompanha status.
 */
@Controller
@Validated
@PreAuthorize("hasAuthority('ENTREGA_GERENCIAR')")
public class EntregaGestaoController {

    private final EntregaRotaService entregaRotaService;

    public EntregaGestaoController(EntregaRotaService entregaRotaService) {
        this.entregaRotaService = entregaRotaService;
    }

    @GetMapping("/gestao/entregas")
    public String listar(Model model) {
        model.addAttribute("vendasElegiveis", entregaRotaService.listarVendasElegiveis());
        model.addAttribute("rotas", entregaRotaService.listarRotasRecentes());
        model.addAttribute("alertasGraves", entregaRotaService.listarOcorrenciasGravesAbertas());
        return "pages/gestao/entregas/lista";
    }

    @PostMapping("/gestao/entregas/previsualizar")
    public String previsualizar(@RequestParam @NotEmpty List<Long> vendaIds, @RequestParam(required = false) String origem,
                                 Model model) {
        model.addAttribute("vendasElegiveis", entregaRotaService.listarVendasElegiveis());
        model.addAttribute("rotas", entregaRotaService.listarRotasRecentes());
        model.addAttribute("vendaIdsSelecionadas", vendaIds);
        model.addAttribute("origemDigitada", origem);
        try {
            DeliveryRouteService.PlannedRoute preview = entregaRotaService.previsualizar(vendaIds, origem);
            model.addAttribute("preview", preview);
        } catch (RuntimeException ex) {
            model.addAttribute("erroPreview", ex.getMessage());
        }
        return "pages/gestao/entregas/lista";
    }

    @PostMapping("/gestao/entregas")
    public String criar(@RequestParam @NotEmpty List<Long> vendaIds, @RequestParam(required = false) String origem,
                         @AuthenticationPrincipal AdminPrincipal principal) {
        EntregaRota rota = entregaRotaService.criarRota(vendaIds, origem, principal.usuarioId());
        return "redirect:/gestao/entregas/rotas/" + rota.getId();
    }

    @GetMapping("/gestao/entregas/rotas/{id}")
    public String detalhe(@PathVariable Long id, Model model) {
        EntregaRota rota = entregaRotaService.obterRota(id);
        model.addAttribute("rota", rota);
        model.addAttribute("ganho", entregaRotaService.calcularGanho(id));
        model.addAttribute("ocorrencias", entregaRotaService.listarOcorrenciasDaRota(id));
        return "pages/gestao/entregas/detalhe";
    }

    @PostMapping("/gestao/entregas/rotas/{id}/ocorrencias/{ocorrenciaId}/resolver")
    public String resolverOcorrencia(@PathVariable Long id, @PathVariable Long ocorrenciaId,
                                      @AuthenticationPrincipal AdminPrincipal principal) {
        entregaRotaService.resolverOcorrencia(ocorrenciaId, principal.usuarioId());
        return "redirect:/gestao/entregas/rotas/" + id;
    }

    @PostMapping("/gestao/entregas/rotas/{id}/paradas/{paradaId}/regenerar-codigo")
    public String regenerarCodigo(@PathVariable Long id, @PathVariable Long paradaId) {
        entregaRotaService.regenerarCodigo(id, paradaId);
        return "redirect:/gestao/entregas/rotas/" + id;
    }

    @PostMapping("/gestao/entregas/rotas/{id}/cancelar")
    public String cancelar(@PathVariable Long id, @RequestParam(required = false) String motivo) {
        entregaRotaService.cancelarRota(id, motivo);
        return "redirect:/gestao/entregas/rotas/" + id;
    }

    @GetMapping("/gestao/entregas/comissoes")
    public String resumoComissoes(Model model) {
        model.addAttribute("resumos", entregaRotaService.listarResumoComissaoPorMotoboy());
        return "pages/gestao/entregas/comissoes";
    }
}
