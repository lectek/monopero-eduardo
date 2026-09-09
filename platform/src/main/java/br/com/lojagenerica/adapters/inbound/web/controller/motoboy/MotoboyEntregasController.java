package br.com.lojagenerica.adapters.inbound.web.controller.motoboy;

import br.com.lojagenerica.adapters.inbound.web.security.AdminJwtAuthFilter;
import br.com.lojagenerica.adapters.outbound.persistence.entity.AdminUserEntity;
import br.com.lojagenerica.adapters.outbound.persistence.jpa.AdminUserRepository;
import br.com.lojagenerica.application.service.delivery.AdminEntregaRouteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Área do motoboy — mesma conta admin_users (role="MOTOBOY", criada só pelo IMS,
 * ver AdminSaas.java), mesmo JWT do /api/auth/login, mas caminho e permissão
 * próprios (AdminJwtAuthFilter exige role MOTOBOY aqui, ADMIN em /api/admin/**).
 * Reaproveita os mesmos métodos de AdminEntregaRouteService que o painel admin
 * usa pra rota — a diferença é só o que cada papel enxerga/pode chamar.
 */
@RestController
@RequestMapping("/api/motoboy/entregas")
@Validated
public class MotoboyEntregasController {

    private final AdminEntregaRouteService adminEntregaRouteService;
    private final AdminUserRepository adminUserRepository;

    public MotoboyEntregasController(
            AdminEntregaRouteService adminEntregaRouteService,
            AdminUserRepository adminUserRepository
    ) {
        this.adminEntregaRouteService = adminEntregaRouteService;
        this.adminUserRepository = adminUserRepository;
    }

    private AdminUserEntity motoboyLogado(HttpServletRequest request) {
        String email = (String) request.getAttribute(AdminJwtAuthFilter.ATTR_EMAIL);
        return adminUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Motoboy não encontrado."));
    }

    @GetMapping("/minhas-rotas")
    public List<AdminEntregaRouteService.RouteSummaryView> minhasRotas(HttpServletRequest request) {
        return adminEntregaRouteService.listMinhasOuDisponiveisRoutes(motoboyLogado(request).getId());
    }

    @GetMapping("/rotas/{rotaId}")
    public AdminEntregaRouteService.DriverRouteView detalharRota(@PathVariable Long rotaId) {
        return adminEntregaRouteService.getDriverRouteView(rotaId);
    }

    @PostMapping("/rotas/{rotaId}/iniciar")
    @Transactional
    public AdminEntregaRouteService.RouteDetailView iniciarRota(@PathVariable Long rotaId, HttpServletRequest request) {
        String email = (String) request.getAttribute(AdminJwtAuthFilter.ATTR_EMAIL);
        return adminEntregaRouteService.startRoute(rotaId, email);
    }

    @PostMapping("/rotas/{rotaId}/paradas/{paradaId}/chegada")
    @Transactional
    public AdminEntregaRouteService.DriverStopDetailView registrarChegada(
            @PathVariable Long rotaId, @PathVariable Long paradaId
    ) {
        return adminEntregaRouteService.markStopArrived(rotaId, paradaId);
    }

    @PostMapping("/rotas/{rotaId}/paradas/{paradaId}/confirmar")
    @Transactional
    public AdminEntregaRouteService.DriverRouteView confirmarParada(
            @PathVariable Long rotaId,
            @PathVariable Long paradaId,
            @Valid @RequestBody ConfirmarParadaRequest request
    ) {
        return adminEntregaRouteService.confirmStop(
                rotaId, paradaId,
                new AdminEntregaRouteService.DeliveryClosureInput(
                        request.formaPagamentoRecebida(), request.avaliacaoEntrega(),
                        request.ocorrencias(), request.observacao()
                )
        );
    }

    @PostMapping("/rotas/{rotaId}/localizacao")
    @Transactional
    public AdminEntregaRouteService.DriverRouteView atualizarLocalizacao(
            @PathVariable Long rotaId, @Valid @RequestBody AtualizarLocalizacaoRequest request
    ) {
        return adminEntregaRouteService.updateDriverLocation(rotaId, request.latitude(), request.longitude());
    }

    /** Quanto o motoboy ganha nessa rota, e conciliação do dinheiro coletado na entrega. */
    @GetMapping("/rotas/{rotaId}/ganho")
    public AdminEntregaRouteService.MotoboyEarningsView ganho(@PathVariable Long rotaId) {
        return adminEntregaRouteService.calcularGanhoMotoboy(rotaId);
    }

    public record ConfirmarParadaRequest(
            String formaPagamentoRecebida,
            Integer avaliacaoEntrega,
            List<String> ocorrencias,
            String observacao
    ) {
    }

    public record AtualizarLocalizacaoRequest(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude
    ) {
    }
}
