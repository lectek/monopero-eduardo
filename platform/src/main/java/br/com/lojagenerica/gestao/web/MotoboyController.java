package br.com.lojagenerica.gestao.web;

import br.com.lojagenerica.core.acesso.Usuario;
import br.com.lojagenerica.core.acesso.UsuarioRepository;
import br.com.lojagenerica.core.entrega.EntregaRota;
import br.com.lojagenerica.core.entrega.EntregaRotaService;
import br.com.lojagenerica.core.entrega.StatusEntregaParada;
import br.com.lojagenerica.security.admin.AdminPrincipal;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
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
 * Tela do motoboy: rotas disponíveis pra assumir + minhas rotas em
 * andamento/concluídas. Antes de "iniciar", o motoboy já vê quanto vai
 * ganhar (comissão projetada sobre o frete das paradas) — é o requisito
 * central do módulo (ver docs/CONTEXTO.md).
 *
 * <p>Sob {@code /gestao/motoboy} (não {@code /motoboy}) de propósito: assim
 * cai na mesma {@code SecurityFilterChain} de sessão de
 * {@code /gestao/**} (ver {@code AdminSecurityConfig}, matcher fixo em
 * {@code /gestao/**}) sem precisar de uma chain nova — motoboy é só um
 * {@code Usuario} com o papel {@code ENTREGA_EXECUTAR}, mesma sessão/login.
 */
@Controller
@Validated
@PreAuthorize("hasAuthority('ENTREGA_EXECUTAR')")
public class MotoboyController {

    private static final String REDIRECT_ROTA = "redirect:/gestao/motoboy/rotas/";

    private final EntregaRotaService entregaRotaService;
    private final UsuarioRepository usuarioRepository;

    public MotoboyController(EntregaRotaService entregaRotaService, UsuarioRepository usuarioRepository) {
        this.entregaRotaService = entregaRotaService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/gestao/motoboy")
    public String minhasRotas(@AuthenticationPrincipal AdminPrincipal principal, Model model) {
        List<EntregaRota> disponiveis = entregaRotaService.listarRotasDisponiveisParaMotoboy();
        List<EntregaRota> minhas = entregaRotaService.listarMinhasRotas(principal.usuarioId());
        model.addAttribute("rotasDisponiveis", disponiveis);
        model.addAttribute("minhasRotas", minhas);
        return "pages/gestao/motoboy/lista";
    }

    @GetMapping("/gestao/motoboy/rotas/{id}")
    public String detalhe(@PathVariable Long id, Model model) {
        EntregaRota rota = entregaRotaService.obterRota(id);
        model.addAttribute("rota", rota);
        model.addAttribute("ganho", entregaRotaService.calcularGanho(id));
        model.addAttribute("proximaParada", rota.getParadas().stream().filter(p -> !p.isConcluida()).findFirst().orElse(null));
        return "pages/gestao/motoboy/detalhe";
    }

    @PostMapping("/gestao/motoboy/rotas/{id}/iniciar")
    public String iniciar(@PathVariable Long id, @AuthenticationPrincipal AdminPrincipal principal) {
        entregaRotaService.iniciarRota(id, usuarioAutenticado(principal));
        return REDIRECT_ROTA + id;
    }

    @PostMapping("/gestao/motoboy/rotas/{id}/paradas/{paradaId}/chegada")
    public String registrarChegada(@PathVariable Long id, @PathVariable Long paradaId,
                                    @AuthenticationPrincipal AdminPrincipal principal) {
        entregaRotaService.registrarChegada(id, paradaId, usuarioAutenticado(principal));
        return REDIRECT_ROTA + id;
    }

    @PostMapping("/gestao/motoboy/rotas/{id}/paradas/{paradaId}/confirmar")
    public String confirmarEntrega(@PathVariable Long id, @PathVariable Long paradaId,
                                    @RequestParam(required = false) String formaPagamentoRecebida,
                                    @RequestParam(required = false) Integer avaliacaoEntrega,
                                    @RequestParam(required = false) List<String> ocorrencias,
                                    @RequestParam(required = false) String observacao,
                                    @AuthenticationPrincipal AdminPrincipal principal) {
        String ocorrenciasTexto = ocorrencias == null ? null : String.join(", ", ocorrencias);
        entregaRotaService.confirmarEntrega(id, paradaId, usuarioAutenticado(principal), formaPagamentoRecebida,
                avaliacaoEntrega, ocorrenciasTexto, observacao);
        return REDIRECT_ROTA + id;
    }

    @PostMapping("/gestao/motoboy/rotas/{id}/paradas/{paradaId}/falha")
    public String registrarFalha(@PathVariable Long id, @PathVariable Long paradaId,
                                  @RequestParam @NotNull StatusEntregaParada falhaStatus,
                                  @RequestParam(required = false) String motivo,
                                  @RequestParam(required = false) String observacao,
                                  @AuthenticationPrincipal AdminPrincipal principal) {
        entregaRotaService.registrarFalha(id, paradaId, usuarioAutenticado(principal), falhaStatus, motivo, observacao);
        return REDIRECT_ROTA + id;
    }

    /**
     * Ping de GPS (chamado por JS via {@code navigator.geolocation.watchPosition},
     * ver template) — sem redirect, é uma chamada em segundo plano enquanto
     * a tela fica aberta, não uma submissão de formulário.
     */
    @PostMapping("/gestao/motoboy/rotas/{id}/localizacao")
    public ResponseEntity<Void> atualizarLocalizacao(@PathVariable Long id, @RequestParam double latitude,
                                                      @RequestParam double longitude,
                                                      @AuthenticationPrincipal AdminPrincipal principal) {
        entregaRotaService.atualizarLocalizacao(id, usuarioAutenticado(principal), latitude, longitude);
        return ResponseEntity.noContent().build();
    }

    private Usuario usuarioAutenticado(AdminPrincipal principal) {
        return usuarioRepository.findById(principal.usuarioId())
                .orElseThrow(() -> new NoSuchElementException("Usuário " + principal.usuarioId() + " não encontrado"));
    }
}
