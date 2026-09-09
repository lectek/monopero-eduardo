package br.com.lojagenerica.adapters.inbound.web.controller.admin;

import br.com.lojagenerica.adapters.inbound.web.security.AdminJwtAuthFilter;
import br.com.lojagenerica.adapters.outbound.persistence.entity.PedidoEntity;
import br.com.lojagenerica.adapters.outbound.persistence.repository.PedidoRepository;
import br.com.lojagenerica.application.service.delivery.AdminEntregaRouteService;
import br.com.lojagenerica.application.support.DeliveryCodeGenerator;
import br.com.lojagenerica.domain.enums.StatusPedido;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/entregas")
@Validated
public class AdminEntregasRestController {

    private final PedidoRepository pedidoRepository;
    private final AdminEntregaRouteService adminEntregaRouteService;

    public AdminEntregasRestController(
            final PedidoRepository pedidoRepositoryValue,
            final AdminEntregaRouteService adminEntregaRouteServiceValue
    ) {
        this.pedidoRepository = pedidoRepositoryValue;
        this.adminEntregaRouteService = adminEntregaRouteServiceValue;
    }

    @GetMapping("/pedidos-elegiveis")
    public List<AdminEntregaRouteService.EligibleOrderView> listarPedidosElegiveis(
            @RequestParam(name = "q", required = false) final String q
    ) {
        return adminEntregaRouteService.listEligibleOrders(q);
    }

    @GetMapping("/rotas")
    public List<AdminEntregaRouteService.RouteSummaryView> listarRotas() {
        return adminEntregaRouteService.listRecentRoutes();
    }

    @GetMapping("/rotas/{rotaId}")
    public AdminEntregaRouteService.RouteDetailView detalharRota(
            @PathVariable("rotaId") final Long rotaId
    ) {
        return adminEntregaRouteService.getRouteDetail(rotaId);
    }

    @GetMapping("/rotas/{rotaId}/motoboy")
    public AdminEntregaRouteService.DriverRouteView detalharRotaMotoboy(
            @PathVariable("rotaId") final Long rotaId
    ) {
        return adminEntregaRouteService.getDriverRouteView(rotaId);
    }

    @PostMapping("/rotas/{rotaId}/localizacao")
    @Transactional
    public AdminEntregaRouteService.DriverRouteView atualizarLocalizacaoDoMotoboy(
            @PathVariable("rotaId") final Long rotaId,
            @Valid @RequestBody final AtualizarLocalizacaoRequest request
    ) {
        return adminEntregaRouteService.updateDriverLocation(
                rotaId,
                request.latitude(),
                request.longitude()
        );
    }

    @PostMapping(
            value = "/roteirizar",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @Transactional
    public AdminEntregaRouteService.PreviewRouteView roteirizar(
            @Valid @RequestBody final RoteirizarRequest request
    ) {
        return previewRoute(request);
    }

    @PostMapping(
            value = "/roteirizar",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    @Transactional
    public AdminEntregaRouteService.PreviewRouteView roteirizarForm(
            @Valid final RoteirizarFormRequest request
    ) {
        return previewRoute(request.toRequest());
    }

    @PostMapping(
            value = "/rotas",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @Transactional
    public AdminEntregaRouteService.RouteDetailView criarRota(
            @Valid @RequestBody final RoteirizarRequest request,
            final HttpServletRequest httpRequest
    ) {
        return createRoute(request, httpRequest);
    }

    @PostMapping(
            value = "/rotas",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    @Transactional
    public AdminEntregaRouteService.RouteDetailView criarRotaForm(
            @Valid final RoteirizarFormRequest request,
            final HttpServletRequest httpRequest
    ) {
        return createRoute(request.toRequest(), httpRequest);
    }

    private AdminEntregaRouteService.PreviewRouteView previewRoute(
            final RoteirizarRequest request
    ) {
        if (hasOriginCoordinates(request)) {
            return adminEntregaRouteService.previewRoute(
                    request.pedidoIds(),
                    request.origem(),
                    request.origemLatitude(),
                    request.origemLongitude()
            );
        }
        return adminEntregaRouteService.previewRoute(
                request.pedidoIds(),
                request.origem()
        );
    }

    private AdminEntregaRouteService.RouteDetailView createRoute(
            final RoteirizarRequest request,
            final HttpServletRequest httpRequest
    ) {
        final String actor = (String) httpRequest.getAttribute(AdminJwtAuthFilter.ATTR_EMAIL);
        if (hasOriginCoordinates(request)) {
            return adminEntregaRouteService.createRoute(
                    request.pedidoIds(),
                    request.origem(),
                    actor,
                    request.origemLatitude(),
                    request.origemLongitude()
            );
        }
        return adminEntregaRouteService.createRoute(
                request.pedidoIds(),
                request.origem(),
                actor
        );
    }

    private boolean hasOriginCoordinates(final RoteirizarRequest request) {
        return request.origemLatitude() != null
                && request.origemLongitude() != null;
    }

    @PostMapping("/rotas/{rotaId}/iniciar")
    @Transactional
    public AdminEntregaRouteService.RouteDetailView iniciarRota(
            @PathVariable("rotaId") final Long rotaId,
            final HttpServletRequest httpRequest
    ) {
        final String actor = (String) httpRequest.getAttribute(AdminJwtAuthFilter.ATTR_EMAIL);
        return adminEntregaRouteService.startRoute(rotaId, actor);
    }

    /**
     * Faltava: {@code AdminEntregaRouteService.markStopArrived} existia mas não tinha
     * rota — sem isso não tinha como sair de A_CAMINHO pra CHEGOU, e confirmarParadaDaRota
     * rejeita justamente paradas em A_CAMINHO ("Registre que chegou ao local antes de
     * confirmar a entrega"). Achado testando o painel de entregas de ponta a ponta.
     */
    @PostMapping("/rotas/{rotaId}/paradas/{paradaId}/chegada")
    @Transactional
    public AdminEntregaRouteService.DriverStopDetailView registrarChegadaNaParada(
            @PathVariable("rotaId") final Long rotaId,
            @PathVariable("paradaId") final Long paradaId
    ) {
        return adminEntregaRouteService.markStopArrived(rotaId, paradaId);
    }

    @PostMapping("/rotas/{rotaId}/paradas/{paradaId}/confirmar")
    @Transactional
    public AdminEntregaRouteService.DriverRouteView confirmarParadaDaRota(
            @PathVariable("rotaId") final Long rotaId,
            @PathVariable("paradaId") final Long paradaId,
            @Valid @RequestBody final ConfirmarParadaRequest request
    ) {
        return adminEntregaRouteService.confirmStop(
                rotaId,
                paradaId,
                new AdminEntregaRouteService.DeliveryClosureInput(
                        request.formaPagamentoRecebida(),
                        request.avaliacaoEntrega(),
                        request.ocorrencias(),
                        request.observacao()
                )
        );
    }

    @PostMapping("/{pedidoId}/confirmar")
    @Transactional
    public ConfirmarEntregaResponse confirmarEntrega(
            @PathVariable("pedidoId") final Long pedidoId,
            @Valid @RequestBody final ConfirmarEntregaRequest request
    ) {
        final PedidoEntity pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Pedido nao encontrado."
                ));
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pedido cancelado nao pode ser confirmado."
            );
        }

        final String expected = normalizeCode(pedido.getCodigoEntrega());
        if (expected.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pedido sem codigo de entrega. Gere um novo codigo."
            );
        }
        final String informed = normalizeCode(request.codigo());
        if (!expected.equals(informed)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Codigo de entrega invalido."
            );
        }

        if (pedido.getCodigoEntregaConfirmadoEm() == null) {
            pedido.setCodigoEntregaConfirmadoEm(LocalDateTime.now());
        }
        pedido.setStatus(StatusPedido.ENTREGUE);
        pedidoRepository.save(pedido);
        adminEntregaRouteService.markOrderDelivered(
                pedido,
                pedido.getCodigoEntregaConfirmadoEm()
        );

        return new ConfirmarEntregaResponse(
                pedido.getId(),
                pedido.getStatus().name(),
                pedido.getCodigoEntregaConfirmadoEm()
        );
    }

    @PostMapping("/{pedidoId}/codigo/regenerar")
    @Transactional
    public CodigoEntregaResponse regenerarCodigo(
            @PathVariable("pedidoId") final Long pedidoId
    ) {
        final PedidoEntity pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Pedido nao encontrado."
                ));
        if (pedido.getStatus() == StatusPedido.ENTREGUE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pedido ja entregue. Nao e permitido regenerar codigo."
            );
        }

        pedido.setCodigoEntrega(DeliveryCodeGenerator.nextCode());
        pedido.setCodigoEntregaGeradoEm(LocalDateTime.now());
        pedido.setCodigoEntregaConfirmadoEm(null);
        pedidoRepository.save(pedido);
        adminEntregaRouteService.syncOrderDeliveryCode(pedido);

        return new CodigoEntregaResponse(
                pedido.getId(),
                pedido.getCodigoEntrega(),
                pedido.getCodigoEntregaGeradoEm()
        );
    }

    private static String normalizeCode(final String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }

    public record RoteirizarRequest(
            @NotEmpty List<@NotNull Long> pedidoIds,
            @Size(max = 255) String origem,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal origemLatitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal origemLongitude
    ) {
    }

    public record ConfirmarEntregaRequest(
            @NotBlank @Pattern(regexp = "\\d{6}") String codigo
    ) {
    }

    public record ConfirmarParadaRequest(
            @Size(max = 30) String formaPagamentoRecebida,
            Integer avaliacaoEntrega,
            List<@NotBlank String> ocorrencias,
            @Size(max = 500) String observacao
    ) {
    }

    public record AtualizarLocalizacaoRequest(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude
    ) {
    }

    public static final class RoteirizarFormRequest {

        @NotEmpty
        private List<@NotNull Long> pedidoIds = new ArrayList<>();

        @Size(max = 255)
        private String origem;

        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        private BigDecimal origemLatitude;

        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        private BigDecimal origemLongitude;

        public List<Long> getPedidoIds() {
            return pedidoIds;
        }

        public void setPedidoIds(final List<Long> pedidoIdsValue) {
            this.pedidoIds = pedidoIdsValue == null
                    ? new ArrayList<>()
                    : new ArrayList<>(pedidoIdsValue);
        }

        public String getOrigem() {
            return origem;
        }

        public void setOrigem(final String origemValue) {
            this.origem = origemValue;
        }

        public BigDecimal getOrigemLatitude() {
            return origemLatitude;
        }

        public void setOrigemLatitude(final BigDecimal origemLatitudeValue) {
            this.origemLatitude = origemLatitudeValue;
        }

        public BigDecimal getOrigemLongitude() {
            return origemLongitude;
        }

        public void setOrigemLongitude(final BigDecimal origemLongitudeValue) {
            this.origemLongitude = origemLongitudeValue;
        }

        private RoteirizarRequest toRequest() {
            return new RoteirizarRequest(
                    pedidoIds,
                    origem,
                    origemLatitude,
                    origemLongitude
            );
        }
    }

    public record ConfirmarEntregaResponse(
            Long pedidoId,
            String status,
            LocalDateTime confirmadoEm
    ) {
    }

    public record CodigoEntregaResponse(
            Long pedidoId,
            String codigoEntrega,
            LocalDateTime geradoEm
    ) {
    }
}
