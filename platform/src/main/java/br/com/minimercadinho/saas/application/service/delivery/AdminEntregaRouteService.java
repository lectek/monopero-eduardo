package br.com.minimercadinho.saas.application.service.delivery;

import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.EntregaParadaEntity;
import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.EntregaRotaEntity;
import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.PedidoEntity;
import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.ClienteNotificacaoEntity;
import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.CustomerEntity;
import br.com.minimercadinho.saas.adapters.outbound.persistence.repository.ClienteNotificacaoRepository;
import br.com.minimercadinho.saas.adapters.outbound.persistence.repository.EntregaParadaRepository;
import br.com.minimercadinho.saas.adapters.outbound.persistence.repository.EntregaRotaRepository;
import br.com.minimercadinho.saas.adapters.outbound.persistence.repository.PedidoRepository;
import br.com.minimercadinho.saas.adapters.outbound.persistence.repository.CustomerRepository;
import br.com.minimercadinho.saas.application.config.AppProps;
import br.com.minimercadinho.saas.application.service.PaymentMethodService;
import br.com.minimercadinho.saas.application.support.DeliveryCodeGenerator;
import br.com.minimercadinho.saas.application.support.NavigationLinkSupport;
import br.com.minimercadinho.saas.domain.enums.EntregaParadaStatus;
import br.com.minimercadinho.saas.domain.enums.EntregaRotaStatus;
import br.com.minimercadinho.saas.domain.enums.ModoEntrega;
import br.com.minimercadinho.saas.domain.enums.StatusPedido;
import br.com.minimercadinho.saas.domain.enums.TipoPagamento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminEntregaRouteService {

    private static final int MIN_PEDIDOS_ROTA = 3;
    private static final int MAX_PEDIDOS_ROTA = 10;
    private static final int MAX_ELIGIBLE_ORDERS = 200;
    private static final int MAX_RECENT_ROUTES = 20;
    private static final BigDecimal APPROACHING_DISTANCE_KM =
            new BigDecimal("0.40");
    private static final BigDecimal DEFAULT_TRACKING_SPEED_KMH =
            new BigDecimal("28.00");
    private static final String ROUTE_NOT_FOUND_MESSAGE = "Rota nao encontrada.";
    private static final String NO_PENDING_STOP_MESSAGE =
            "Nao ha parada pendente nessa rota.";
    private static final String DEFAULT_LABEL = "Cliente";
    private static final String DEFAULT_UNKNOWN = "-";
    private static final String STATUS_UNKNOWN = "DESCONHECIDO";
    private static final String PAYMENT_DINHEIRO = "DINHEIRO";
    private static final String PAYMENT_CARTAO = "CARTAO";
    private static final String PAYMENT_OUTRO = "OUTRO";
    private static final String TYPE_ENTREGA = "ENTREGA";
    private static final String LABEL_OUTRO = "Outro";
    private static final String FAILURE_AUSENTE = "AUSENTE";
    private static final String FAILURE_ENDERECO_RISCO = "ENDERECO_RISCO";
    private static final String FAILURE_RECUSA_PAGAMENTO = "RECUSA_PAGAMENTO";
    private static final List<StatusPedido> ROUTABLE_ORDER_STATUSES = List.of(
            StatusPedido.PRONTO_PARA_ENTREGA,
            StatusPedido.SAIU_PARA_ENTREGA
    );
    private static final List<StatusPedido> REQUESTED_ORDER_EXCLUDED_STATUSES =
            List.of(
                    StatusPedido.ENTREGUE,
                    StatusPedido.CANCELADO
            );
    private static final List<EntregaRotaStatus> ACTIVE_ROUTE_STATUSES = List.of(
            EntregaRotaStatus.PLANEJADA,
            EntregaRotaStatus.DESPACHADA,
            EntregaRotaStatus.EM_EXECUCAO
    );

    private final PedidoRepository pedidoRepository;
    private final CustomerRepository customerRepository;
    /** Só para {@link #resolveActor} — quem despacha/inicia a rota é a equipe da loja, não o cliente do pedido. */
    private final br.com.minimercadinho.saas.adapters.outbound.persistence.jpa.AdminUserRepository adminUserRepository;
    private final DeliveryRouteService deliveryRouteService;
    private final EntregaRotaRepository entregaRotaRepository;
    private final EntregaParadaRepository entregaParadaRepository;
    private final ClienteNotificacaoRepository notificacaoRepository;
    private final PaymentMethodService paymentMethodService;
    private final AppProps appProps;

    public AdminEntregaRouteService(
            final PedidoRepository pedidoRepositoryValue,
            final CustomerRepository customerRepositoryValue,
            final br.com.minimercadinho.saas.adapters.outbound.persistence.jpa.AdminUserRepository adminUserRepositoryValue,
            final DeliveryRouteService deliveryRouteServiceValue,
            final EntregaRotaRepository entregaRotaRepositoryValue,
            final EntregaParadaRepository entregaParadaRepositoryValue,
            final ClienteNotificacaoRepository notificacaoRepositoryValue,
            final PaymentMethodService paymentMethodServiceValue,
            final AppProps appPropsValue
    ) {
        this.pedidoRepository = pedidoRepositoryValue;
        this.customerRepository = customerRepositoryValue;
        this.adminUserRepository = adminUserRepositoryValue;
        this.deliveryRouteService = deliveryRouteServiceValue;
        this.entregaRotaRepository = entregaRotaRepositoryValue;
        this.entregaParadaRepository = entregaParadaRepositoryValue;
        this.notificacaoRepository = notificacaoRepositoryValue;
        this.paymentMethodService = paymentMethodServiceValue;
        this.appProps = appPropsValue;
    }

    @Transactional(readOnly = true)
    public DashboardView buildDashboard(final String query) {
        final LocalDate today = LocalDate.now();
        final LocalDateTime startOfDay = today.atStartOfDay();
        final LocalDateTime endOfDay = startOfDay.plusDays(1);
        return new DashboardView(
                listEligibleOrdersInternal(query).size(),
                entregaRotaRepository.countByStatusIn(ACTIVE_ROUTE_STATUSES),
                listRecentRoutesInternal().size(),
                entregaParadaRepository.countByStatusInAndConfirmadoEmBetween(
                        List.of(EntregaParadaStatus.ENTREGUE),
                        startOfDay,
                        endOfDay
                )
        );
    }

    @Transactional(readOnly = true)
    public List<EligibleOrderView> listEligibleOrders(final String query) {
        return listEligibleOrdersInternal(query);
    }

    private List<EligibleOrderView> listEligibleOrdersInternal(final String query) {
        final Collection<Long> blockedPedidoIds = entregaParadaRepository
                .findPedidoIdsInRouteStatuses(ACTIVE_ROUTE_STATUSES);
        return pedidoRepository
                .listarPorModoEntregaEStatusComCliente(
                        ModoEntrega.ENTREGA,
                        ROUTABLE_ORDER_STATUSES,
                        PageRequest.of(0, MAX_ELIGIBLE_ORDERS)
                )
                .stream()
                .filter(pedido -> !blockedPedidoIds.contains(pedido.getId()))
                .map(this::toEligibleOrderView)
                .flatMap(Optional::stream)
                .filter(item -> matchesQuery(item, query))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryRequestView> listDeliveryRequests(final String query) {
        final Collection<Long> blockedPedidoIds = entregaParadaRepository
                .findPedidoIdsInRouteStatuses(ACTIVE_ROUTE_STATUSES);
        final List<BaseDeliveryRequestView> filteredRequests = pedidoRepository
                .listarPorModoEntregaExcluindoStatusComCliente(
                        ModoEntrega.ENTREGA,
                        REQUESTED_ORDER_EXCLUDED_STATUSES,
                        PageRequest.of(0, MAX_ELIGIBLE_ORDERS)
                )
                .stream()
                .map(pedido -> toBaseDeliveryRequestView(
                        pedido,
                        blockedPedidoIds.contains(pedido.getId())
                ))
                .flatMap(Optional::stream)
                .filter(item -> matchesQuery(item, query))
                .toList();

        final List<DeliveryRouteService.LegDistanceEstimate> distances =
                deliveryRouteService.estimateSequentialDistances(
                        appProps.getAddressQuery(),
                        filteredRequests.stream()
                                .map(BaseDeliveryRequestView::enderecoEntrega)
                                .toList()
                );

        final ArrayList<DeliveryRequestView> result = new ArrayList<>(
                filteredRequests.size()
        );
        for (int index = 0; index < filteredRequests.size(); index++) {
            final BaseDeliveryRequestView item = filteredRequests.get(index);
            final DeliveryRouteService.LegDistanceEstimate leg =
                    index < distances.size() ? distances.get(index) : null;
            result.add(new DeliveryRequestView(
                    item.id(),
                    item.numero(),
                    item.clienteNome(),
                    item.enderecoEntrega(),
                    item.total(),
                    item.status(),
                    item.statusLabel(),
                    item.codigoEntrega(),
                    item.data(),
                    item.googleMapsUrl(),
                    item.wazeUrl(),
                    leg != null ? leg.referenciaAnterior() : "Base",
                    leg != null ? leg.distanciaKm() : null,
                    item.elegivelParaRota(),
                    item.emRota()
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<RouteSummaryView> listRecentRoutes() {
        return listRecentRoutesInternal();
    }

    private static final List<EntregaRotaStatus> ROTA_DISPONIVEL_PARA_MOTOBOY = List.of(
            EntregaRotaStatus.PLANEJADA, EntregaRotaStatus.DESPACHADA
    );

    /** Painel do motoboy: rotas já dele (qualquer status) + disponíveis pra pegar (sem entregador ainda). */
    @Transactional(readOnly = true)
    public List<RouteSummaryView> listMinhasOuDisponiveisRoutes(final Long motoboyId) {
        return entregaRotaRepository
                .findMinhasOuDisponiveis(motoboyId, ROTA_DISPONIVEL_PARA_MOTOBOY)
                .stream()
                .map(this::toRouteSummaryView)
                .toList();
    }

    private List<RouteSummaryView> listRecentRoutesInternal() {
        return entregaRotaRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, MAX_RECENT_ROUTES))
                .stream()
                .map(this::toRouteSummaryView)
                .toList();
    }

    @Transactional(readOnly = true)
    public RouteDetailView getRouteDetail(final Long routeId) {
        return getRouteDetailInternal(routeId);
    }

    @Transactional(readOnly = true)
    public DriverRouteView getDriverRouteView(final Long routeId) {
        return getDriverRouteViewInternal(routeId);
    }

    private RouteDetailView getRouteDetailInternal(final Long routeId) {
        final EntregaRotaEntity route = findRouteOrThrow(routeId);
        final List<EntregaParadaEntity> stops = findRouteStops(routeId);
        return toRouteDetailView(route, stops);
    }

    private DriverRouteView getDriverRouteViewInternal(final Long routeId) {
        final EntregaRotaEntity route = findRouteOrThrow(routeId);
        final List<EntregaParadaEntity> stops = findRouteStops(routeId);
        return toDriverRouteView(route, stops);
    }

    private EntregaRotaEntity findRouteOrThrow(final Long routeId) {
        return entregaRotaRepository.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        ROUTE_NOT_FOUND_MESSAGE
                ));
    }

    private List<EntregaParadaEntity> findRouteStops(final Long routeId) {
        return entregaParadaRepository.findByRotaIdWithPedidoOrderByOrdemAsc(routeId);
    }

    @Transactional(readOnly = true)
    public DriverStopDetailView getDriverStopDetailView(
            final Long routeId,
            final Long stopId
    ) {
        final EntregaRotaEntity route = entregaRotaRepository.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        ROUTE_NOT_FOUND_MESSAGE
                ));
        final List<EntregaParadaEntity> stops = entregaParadaRepository
                .findByRotaIdWithPedidoOrderByOrdemAsc(routeId);
        return toDriverStopDetailView(route, stops, findStopOrThrow(stops, stopId));
    }

    @Transactional
    public DriverRouteView updateDriverLocation(
            final Long routeId,
            final BigDecimal latitude,
            final BigDecimal longitude
    ) {
        final EntregaRotaEntity route = entregaRotaRepository.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        ROUTE_NOT_FOUND_MESSAGE
                ));
        if (route.getStatus() != EntregaRotaStatus.EM_EXECUCAO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A localizacao so pode ser enviada com a rota em execucao."
            );
        }
        final List<EntregaParadaEntity> stops = entregaParadaRepository
                .findByRotaIdWithPedidoOrderByOrdemAsc(routeId);
        if (stops.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Rota sem paradas para rastreamento."
            );
        }

        route.setMotoristaLatitude(normalizeCoordinate(latitude, 6));
        route.setMotoristaLongitude(normalizeCoordinate(longitude, 6));
        route.setMotoristaLocalizacaoEm(LocalDateTime.now());

        final Optional<EntregaParadaEntity> nextStop = findNextStop(stops);
        maybeNotifyCustomerApproaching(route, nextStop.orElse(null));

        entregaRotaRepository.save(route);
        return toDriverRouteView(route, stops);
    }

    @Transactional
    public DriverStopDetailView markStopArrived(
            final Long routeId,
            final Long stopId
    ) {
        final EntregaRotaEntity route = entregaRotaRepository.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        ROUTE_NOT_FOUND_MESSAGE
                ));
        if (route.getStatus() != EntregaRotaStatus.EM_EXECUCAO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Inicie a rota antes de registrar a chegada."
            );
        }
        final List<EntregaParadaEntity> stops = entregaParadaRepository
                .findByRotaIdWithPedidoOrderByOrdemAsc(routeId);
        final EntregaParadaEntity stop = findStopOrThrow(stops, stopId);
        final EntregaParadaEntity nextStop = findNextStop(stops)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        NO_PENDING_STOP_MESSAGE
                ));
        if (!stop.getId().equals(nextStop.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Registre primeiro a chegada da proxima parada da rota."
            );
        }
        if (stop.getStatus() == EntregaParadaStatus.CHEGOU
                || stop.getStatus() == EntregaParadaStatus.ENTREGUE) {
            return toDriverStopDetailView(route, stops, stop);
        }
        if (stop.getStatus() != EntregaParadaStatus.A_CAMINHO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Essa parada ainda nao esta pronta para registrar chegada."
            );
        }

        stop.setStatus(EntregaParadaStatus.CHEGOU);
        entregaParadaRepository.save(stop);
        buildArrivedNotification(stop.getPedido())
                .ifPresent(notificacaoRepository::save);
        return toDriverStopDetailView(route, stops, stop);
    }

    @Transactional
    public DriverRouteView registerStopFailure(
            final Long routeId,
            final Long stopId,
            final DeliveryFailureInput input
    ) {
        final EntregaRotaEntity route = entregaRotaRepository.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        ROUTE_NOT_FOUND_MESSAGE
                ));
        if (route.getStatus() != EntregaRotaStatus.EM_EXECUCAO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Inicie a rota antes de registrar um insucesso."
            );
        }
        final List<EntregaParadaEntity> stops = entregaParadaRepository
                .findByRotaIdWithPedidoOrderByOrdemAsc(routeId);
        final EntregaParadaEntity stop = findStopOrThrow(stops, stopId);
        final EntregaParadaEntity nextStop = findNextStop(stops)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        NO_PENDING_STOP_MESSAGE
                ));
        if (!stop.getId().equals(nextStop.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Registre primeiro o resultado da proxima parada da rota."
            );
        }
        if (stop.getStatus() != EntregaParadaStatus.A_CAMINHO
                && stop.getStatus() != EntregaParadaStatus.CHEGOU) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Essa parada nao aceita registro de insucesso."
            );
        }

        final EntregaParadaStatus failureStatus =
                resolveFailureStatus(input.statusFalha());
        final String failureReason = normalizeFailureReason(input.motivoFalha());
        if (failureReason.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe o motivo do insucesso."
            );
        }

        final LocalDateTime now = LocalDateTime.now();
        stop.setStatus(failureStatus);
        stop.setMotivoFalha(failureReason);
        stop.setObservacao(normalizeObservation(input.observacao()));
        stop.setConfirmadoEm(now);

        final PedidoEntity pedido = stop.getPedido();
        if (pedido != null
                && pedido.getStatus() != StatusPedido.ENTREGUE
                && pedido.getStatus() != StatusPedido.CANCELADO) {
            pedido.setStatus(StatusPedido.PRONTO_PARA_ENTREGA);
            pedidoRepository.save(pedido);
            buildFailureNotification(pedido, failureStatus, failureReason)
                    .ifPresent(notificacaoRepository::save);
        }

        advanceRouteToNextActionableStop(route, stops, now);
        entregaParadaRepository.saveAll(stops);
        entregaRotaRepository.save(route);
        return getDriverRouteViewInternal(routeId);
    }

    @Transactional(readOnly = true)
    public CustomerTrackingView getCustomerTrackingView(final Long pedidoId) {
        final List<EntregaParadaEntity> activeStops = entregaParadaRepository
                .findByPedidoIdInRouteStatuses(pedidoId, ACTIVE_ROUTE_STATUSES);
        if (activeStops.isEmpty()) {
            return CustomerTrackingView.unavailable(pedidoId);
        }
        final EntregaParadaEntity customerStop = activeStops.getFirst();
        if (customerStop.getStatus() == EntregaParadaStatus.REAGENDAR
                || customerStop.getStatus() == EntregaParadaStatus.TENTATIVA_SEM_SUCESSO) {
            return CustomerTrackingView.unavailable(pedidoId);
        }
        final EntregaRotaEntity route = customerStop.getRota();
        if (route == null) {
            return CustomerTrackingView.unavailable(pedidoId);
        }
        final List<EntregaParadaEntity> routeStops = entregaParadaRepository
                .findByRotaIdWithPedidoOrderByOrdemAsc(route.getId());
        return toCustomerTrackingView(pedidoId, route, routeStops, customerStop);
    }

    @Transactional(readOnly = true)
    public CustomerDeliveryIncidentView getLatestCustomerDeliveryIncident(
            final Long pedidoId
    ) {
        return entregaParadaRepository.findLatestByPedidoIdAndStatuses(
                        pedidoId,
                        List.of(
                                EntregaParadaStatus.REAGENDAR,
                                EntregaParadaStatus.TENTATIVA_SEM_SUCESSO
                        ),
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst()
                .map(this::toCustomerDeliveryIncidentView)
                .orElse(CustomerDeliveryIncidentView.unavailable());
    }

    @Transactional(readOnly = true)
    public PreviewRouteView previewRoute(
            final List<Long> pedidoIds,
            final String origem
    ) {
        return previewRouteInternal(pedidoIds, origem, null, null);
    }

    @Transactional(readOnly = true)
    public PreviewRouteView previewRoute(
            final List<Long> pedidoIds,
            final String origem,
            final BigDecimal origemLatitude,
            final BigDecimal origemLongitude
    ) {
        return previewRouteInternal(pedidoIds, origem, origemLatitude, origemLongitude);
    }

    private PreviewRouteView previewRouteInternal(
            final List<Long> pedidoIds,
            final String origem,
            final BigDecimal origemLatitude,
            final BigDecimal origemLongitude
    ) {
        final PlannedSelection planned = planSelection(
                pedidoIds,
                origem,
                origemLatitude,
                origemLongitude
        );
        return toPreviewRouteView(planned.plannedRoute());
    }

    @Transactional
    public RouteDetailView createRoute(
            final List<Long> pedidoIds,
            final String origem,
            final String actorIdentity
    ) {
        return createRouteInternal(pedidoIds, origem, actorIdentity, null, null);
    }

    @Transactional
    public RouteDetailView createRoute(
            final List<Long> pedidoIds,
            final String origem,
            final String actorIdentity,
            final BigDecimal origemLatitude,
            final BigDecimal origemLongitude
    ) {
        return createRouteInternal(
                pedidoIds,
                origem,
                actorIdentity,
                origemLatitude,
                origemLongitude
        );
    }

    private RouteDetailView createRouteInternal(
            final List<Long> pedidoIds,
            final String origem,
            final String actorIdentity,
            final BigDecimal origemLatitude,
            final BigDecimal origemLongitude
    ) {
        final PlannedSelection planned = planSelection(
                pedidoIds,
                origem,
                origemLatitude,
                origemLongitude
        );
        final List<Long> uniqueIds = planned.orderedPedidos().stream()
                .map(PedidoEntity::getId)
                .toList();
        ensureOrdersNotInActiveRoutes(uniqueIds);

        final EntregaRotaEntity route = new EntregaRotaEntity();
        route.setDataOperacao(LocalDate.now());
        route.setOrigem(planned.plannedRoute().origem());
        route.setCustoTotal(planned.plannedRoute().custoTotal());
        route.setDistanciaTotalKm(planned.plannedRoute().distanciaTotalKm());
        route.setMapaUrl(planned.plannedRoute().mapaUrl());
        route.setStatus(EntregaRotaStatus.PLANEJADA);
        resolveActor(actorIdentity).ifPresent(route::setCriadaPor);

        final EntregaRotaEntity savedRoute = entregaRotaRepository.save(route);
        final Map<Long, PedidoEntity> ordersById = planned.orderedPedidos().stream()
                .collect(Collectors.toMap(PedidoEntity::getId, pedido -> pedido));

        final List<EntregaParadaEntity> stops = planned.plannedRoute().paradas()
                .stream()
                .map(stop -> toStopEntity(
                        savedRoute,
                        ordersById.get(stop.pedidoId()),
                        stop
                ))
                .toList();
        entregaParadaRepository.saveAll(stops);

        return getRouteDetailInternal(savedRoute.getId());
    }

    @Transactional
    public RouteDetailView startRoute(
            final Long routeId,
            final String actorIdentity
    ) {
        final EntregaRotaEntity route = findRouteOrThrow(routeId);
        final List<EntregaParadaEntity> stops = findRouteStops(routeId);
        validateRouteCanStart(route, stops);

        final LocalDateTime now = LocalDateTime.now();
        resolveActor(actorIdentity).ifPresent(route::setEntregador);
        if (route.getDespachadaEm() == null) {
            route.setDespachadaEm(now);
        }
        if (route.getIniciadaEm() == null) {
            route.setIniciadaEm(now);
        }
        route.setStatus(EntregaRotaStatus.EM_EXECUCAO);

        final List<PedidoEntity> updatedOrders = assignStopsForStartedRoute(stops);

        entregaRotaRepository.save(route);
        entregaParadaRepository.saveAll(stops);
        if (!updatedOrders.isEmpty()) {
            pedidoRepository.saveAll(updatedOrders);
            notifyOrdersOutForDelivery(updatedOrders);
        }
        return toRouteDetailView(route, stops);
    }

    private void validateRouteCanStart(
            final EntregaRotaEntity route,
            final List<EntregaParadaEntity> stops
    ) {
        if (stops.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Rota sem paradas para iniciar."
            );
        }
        if (route.getStatus() == EntregaRotaStatus.EM_EXECUCAO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Essa rota ja foi iniciada."
            );
        }
        if (route.getStatus() == EntregaRotaStatus.CONCLUIDA
                || route.getStatus() == EntregaRotaStatus.CANCELADA
                || route.getStatus() == EntregaRotaStatus.RASCUNHO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Essa rota nao pode mais ser iniciada."
            );
        }
    }

    private List<PedidoEntity> assignStopsForStartedRoute(
            final List<EntregaParadaEntity> stops
    ) {
        boolean firstPendingAssigned = false;
        final List<PedidoEntity> updatedOrders = new ArrayList<>();
        for (EntregaParadaEntity stop : stops) {
            if (stop.getStatus() == EntregaParadaStatus.ENTREGUE
                    || stop.getStatus() == EntregaParadaStatus.CANCELADA) {
                continue;
            }
            stop.setStatus(firstPendingAssigned
                    ? EntregaParadaStatus.PENDENTE
                    : EntregaParadaStatus.A_CAMINHO);
            firstPendingAssigned = true;

            final PedidoEntity pedido = stop.getPedido();
            if (pedido != null
                    && pedido.getStatus() != StatusPedido.ENTREGUE
                    && pedido.getStatus() != StatusPedido.CANCELADO) {
                pedido.setStatus(StatusPedido.SAIU_PARA_ENTREGA);
                updatedOrders.add(pedido);
            }
        }
        return updatedOrders;
    }

    @Transactional
    public DriverRouteView confirmStop(
            final Long routeId,
            final Long stopId,
            final DeliveryClosureInput input
    ) {
        final EntregaRotaEntity route = entregaRotaRepository.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        ROUTE_NOT_FOUND_MESSAGE
                ));
        if (route.getStatus() != EntregaRotaStatus.EM_EXECUCAO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Inicie a rota antes de confirmar a parada."
            );
        }
        final List<EntregaParadaEntity> stops = entregaParadaRepository
                .findByRotaIdWithPedidoOrderByOrdemAsc(routeId);
        final EntregaParadaEntity stop = findStopOrThrow(stops, stopId);
        final EntregaParadaEntity nextStop = findNextStop(stops)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        NO_PENDING_STOP_MESSAGE
                ));
        if (!stop.getId().equals(nextStop.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Confirme primeiro a proxima parada da rota."
            );
        }

        final PedidoEntity pedido = stop.getPedido();
        if (pedido == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pedido vinculado a parada nao encontrado."
            );
        }
        if (stop.getStatus() == EntregaParadaStatus.A_CAMINHO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Registre que chegou ao local antes de confirmar a entrega."
            );
        }
        if (stop.getStatus() != EntregaParadaStatus.CHEGOU) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Essa parada nao esta pronta para confirmacao."
            );
        }
        if (isPaymentOnDelivery(pedido) && isBlank(input.formaPagamentoRecebida())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe como o pagamento foi recebido no local."
            );
        }
        // Diferente do ParaisoPet original: aqui o motoboy PODE receber dinheiro na
        // entrega. A conciliação (quanto é comissão dele, quanto volta pro caixa) é
        // calculada em calcularGanhoMotoboy, não bloqueada aqui.

        final LocalDateTime now = LocalDateTime.now();
        stop.setStatus(EntregaParadaStatus.ENTREGUE);
        stop.setConfirmadoEm(now);
        stop.setCodigoEntregaSnapshot(pedido.getCodigoEntrega());
        stop.setFormaPagamentoRecebida(normalizeReceivedPayment(input.formaPagamentoRecebida()));
        stop.setPagamentoDivergente(
                isBlank(stop.getFormaPagamentoRecebida())
                        ? Boolean.FALSE
                        : !expectedPaymentCategory(pedido).equals(stop.getFormaPagamentoRecebida())
        );
        stop.setAvaliacaoEntrega(sanitizeRating(input.avaliacaoEntrega()));
        stop.setOcorrencias(joinOccurrences(input.ocorrencias()));
        stop.setObservacao(normalizeObservation(input.observacao()));

        pedido.setStatus(StatusPedido.ENTREGUE);
        pedido.setCodigoEntregaConfirmadoEm(now);

        advanceRouteToNextActionableStop(route, stops, now);

        pedidoRepository.save(pedido);
        entregaParadaRepository.saveAll(stops);
        entregaRotaRepository.save(route);
        return getDriverRouteViewInternal(routeId);
    }

    private static final BigDecimal PERCENTUAL_COMISSAO_PADRAO = BigDecimal.valueOf(70);
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    /**
     * Quanto o motoboy ganha nessa rota (percentual dele sobre o frete de cada pedido
     * entregue) e a conciliação do dinheiro físico: o motoboy já fica com a própria
     * comissão do dinheiro que ele mesmo coletou na entrega, então só precisa devolver
     * pro caixa o que sobrar (dinheiro coletado − comissão). Se o dinheiro coletado não
     * cobrir a comissão toda (ex.: a maioria das entregas da rota foi Pix), o resto da
     * comissão não é "dinheiro" nenhum — é sinalizado à parte pra acerto manual com a loja.
     */
    @Transactional(readOnly = true)
    public MotoboyEarningsView calcularGanhoMotoboy(final Long routeId) {
        final EntregaRotaEntity route = entregaRotaRepository.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ROUTE_NOT_FOUND_MESSAGE));
        final List<EntregaParadaEntity> stops = entregaParadaRepository
                .findByRotaIdWithPedidoOrderByOrdemAsc(routeId);

        final BigDecimal percentual = route.getEntregador() != null && route.getEntregador().getPercentualComissao() != null
                ? BigDecimal.valueOf(route.getEntregador().getPercentualComissao())
                : PERCENTUAL_COMISSAO_PADRAO;
        final BigDecimal fatorMotoboy = percentual.divide(CEM, 4, java.math.RoundingMode.HALF_UP);

        BigDecimal freteTotal = BigDecimal.ZERO;
        BigDecimal freteEntregueDinheiro = BigDecimal.ZERO;
        BigDecimal dinheiroColetado = BigDecimal.ZERO;

        for (EntregaParadaEntity stop : stops) {
            if (stop.getStatus() != EntregaParadaStatus.ENTREGUE) {
                continue;
            }
            final PedidoEntity pedido = stop.getPedido();
            final BigDecimal frete = pedido.getValorFrete() != null ? pedido.getValorFrete() : BigDecimal.ZERO;
            freteTotal = freteTotal.add(frete);
            if (PAYMENT_DINHEIRO.equals(stop.getFormaPagamentoRecebida())) {
                freteEntregueDinheiro = freteEntregueDinheiro.add(frete);
                dinheiroColetado = dinheiroColetado.add(pedido.getTotal());
            }
        }

        final BigDecimal comissaoMotoboy = freteTotal.multiply(fatorMotoboy).setScale(2, java.math.RoundingMode.HALF_UP);
        final BigDecimal comissaoDoDinheiroColetado = freteEntregueDinheiro.multiply(fatorMotoboy)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        final BigDecimal valorDevolverLoja = dinheiroColetado.subtract(comissaoDoDinheiroColetado)
                .max(BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
        // Comissão de entregas pagas por fora (Pix/cartão) — o motoboy nunca teve esse
        // dinheiro na mão, então essa parte da comissão não sai do "devolver pra loja",
        // precisa ser acertada com o motoboy por outro meio.
        final BigDecimal comissaoNaoCobertaPorDinheiro = comissaoMotoboy.subtract(comissaoDoDinheiroColetado)
                .max(BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);

        return new MotoboyEarningsView(
                routeId, percentual, freteTotal, comissaoMotoboy,
                dinheiroColetado, valorDevolverLoja, comissaoNaoCobertaPorDinheiro
        );
    }

    public record MotoboyEarningsView(
            Long rotaId,
            BigDecimal percentualComissao,
            BigDecimal valorFreteTotal,
            BigDecimal comissaoMotoboy,
            BigDecimal dinheiroColetado,
            BigDecimal valorDevolverLoja,
            BigDecimal comissaoNaoCobertaPorDinheiro
    ) {
    }

    @Transactional
    public void markOrderDelivered(
            final PedidoEntity pedido,
            final LocalDateTime confirmedAt
    ) {
        final List<EntregaParadaEntity> activeStops = entregaParadaRepository
                .findByPedidoIdInRouteStatuses(
                        pedido.getId(),
                        ACTIVE_ROUTE_STATUSES
                );
        for (EntregaParadaEntity stop : activeStops) {
            stop.setStatus(EntregaParadaStatus.ENTREGUE);
            stop.setConfirmadoEm(confirmedAt);
            stop.setCodigoEntregaSnapshot(pedido.getCodigoEntrega());
        }
        if (!activeStops.isEmpty()) {
            entregaParadaRepository.saveAll(activeStops);
        }
    }

    @Transactional
    public void syncOrderDeliveryCode(final PedidoEntity pedido) {
        final List<EntregaParadaEntity> activeStops = entregaParadaRepository
                .findByPedidoIdInRouteStatuses(
                        pedido.getId(),
                        ACTIVE_ROUTE_STATUSES
                );
        for (EntregaParadaEntity stop : activeStops) {
            stop.setCodigoEntregaSnapshot(pedido.getCodigoEntrega());
        }
        if (!activeStops.isEmpty()) {
            entregaParadaRepository.saveAll(activeStops);
        }
    }

    private PlannedSelection planSelection(
            final List<Long> rawPedidoIds,
            final String origem,
            final BigDecimal origemLatitude,
            final BigDecimal origemLongitude
    ) {
        final List<Long> pedidoIds = uniqueIds(rawPedidoIds);
        validatePedidoCount(pedidoIds);
        final PreparedSelectionData selectionData = prepareSelectionData(pedidoIds);
        persistPreparedPedidos(selectionData);

        final RoutePlanningOrigin routeOrigin = resolveRoutePlanningOrigin(
                origem,
                origemLatitude,
                origemLongitude
        );
        final DeliveryRouteService.PlannedRoute plannedRoute =
                planPreparedSelection(routeOrigin, selectionData.inputs());
        ensureRouteHasCalculatedDistances(plannedRoute, selectionData.inputs().size());
        return new PlannedSelection(
                selectionData.orderedPedidos(),
                plannedRoute
        );
    }

    private void ensureRouteHasCalculatedDistances(
            final DeliveryRouteService.PlannedRoute plannedRoute,
            final int expectedStops
    ) {
        if (plannedRoute == null
                || plannedRoute.distanciaTotalKm() == null
                || plannedRoute.paradas() == null
                || plannedRoute.paradas().size() != expectedStops
                || plannedRoute.paradas().stream().anyMatch(this::hasIncompleteRouteStop)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Nao foi possivel calcular a rota com precisao para todos os pedidos. "
                            + "A rota so pode ser criada quando todas as distancias forem calculadas."
            );
        }
    }

    private boolean hasIncompleteRouteStop(
            final DeliveryRouteService.DeliveryStopPlan stop
    ) {
        return stop == null
                || stop.pedidoId() == null
                || isBlank(stop.enderecoEntrega())
                || stop.distanciaAnteriorKm() == null
                || stop.distanciaAcumuladaKm() == null
                || stop.latitude() == null
                || stop.longitude() == null;
    }

    private PreparedSelectionData prepareSelectionData(final List<Long> pedidoIds) {
        final Map<Long, PedidoEntity> pedidosById = loadPedidosById(pedidoIds);
        final List<PedidoEntity> orderedPedidos = new ArrayList<>();
        final List<DeliveryRouteService.DeliveryStopInput> inputs = new ArrayList<>();
        boolean changed = false;

        for (Long id : pedidoIds) {
            final PedidoEntity pedido = pedidosById.get(id);
            orderedPedidos.add(pedido);
            validatePedidoForRouting(pedido);
            changed = ensurePedidoReadyForRouting(pedido) || changed;
            final String endereco = resolveEnderecoForRouting(pedido);
            inputs.add(buildRouteStopInput(pedido, endereco));
        }
        return new PreparedSelectionData(orderedPedidos, inputs, changed);
    }

    private Map<Long, PedidoEntity> loadPedidosById(final List<Long> pedidoIds) {
        final Map<Long, PedidoEntity> byId = pedidoRepository
                .buscarPorIdsComCliente(pedidoIds)
                .stream()
                .collect(Collectors.toMap(PedidoEntity::getId, pedido -> pedido));
        final List<Long> missing = pedidoIds.stream()
                .filter(id -> !byId.containsKey(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Pedidos nao encontrados: " + missing
            );
        }
        return byId;
    }

    private boolean ensurePedidoReadyForRouting(final PedidoEntity pedido) {
        boolean changed = false;
        if (isBlank(pedido.getCodigoEntrega())) {
            pedido.setCodigoEntrega(DeliveryCodeGenerator.nextCode());
            pedido.setCodigoEntregaGeradoEm(LocalDateTime.now());
            changed = true;
        }
        final String enderecoAtual = normalizeAddress(pedido.getEnderecoEntrega());
        if (!enderecoAtual.isBlank()) {
            return changed;
        }
        final String enderecoUsuario = resolveEnderecoFromUsuario(pedido);
        if (!enderecoUsuario.isBlank()) {
            pedido.setEnderecoEntrega(enderecoUsuario);
            changed = true;
        }
        return changed;
    }

    private String resolveEnderecoForRouting(final PedidoEntity pedido) {
        final String endereco = normalizeAddress(pedido.getEnderecoEntrega());
        if (!endereco.isBlank()) {
            return endereco;
        }
        throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Pedido #" + pedido.getId()
                        + " nao possui endereco de entrega."
        );
    }

    private DeliveryRouteService.DeliveryStopInput buildRouteStopInput(
            final PedidoEntity pedido,
            final String endereco
    ) {
        final String status = pedido.getStatus() != null
                ? pedido.getStatus().name()
                : STATUS_UNKNOWN;
        return new DeliveryRouteService.DeliveryStopInput(
                pedido.getId(),
                resolveClienteNome(pedido),
                endereco,
                pedido.getCodigoEntrega(),
                status
        );
    }

    private void persistPreparedPedidos(final PreparedSelectionData selectionData) {
        if (selectionData.changed()) {
            pedidoRepository.saveAll(selectionData.orderedPedidos());
        }
    }

    private RoutePlanningOrigin resolveRoutePlanningOrigin(
            final String origem,
            final BigDecimal origemLatitude,
            final BigDecimal origemLongitude
    ) {
        if (origemLatitude != null && origemLongitude != null) {
            final String originLabel = resolveOriginLabel(
                    origem,
                    "Localizacao atual do dispositivo"
            );
            return new RoutePlanningOrigin(
                    originLabel,
                    origemLatitude.doubleValue(),
                    origemLongitude.doubleValue()
            );
        }
        return new RoutePlanningOrigin(
                resolveOriginLabel(origem, appProps.getAddressQuery()),
                null,
                null
        );
    }

    private DeliveryRouteService.PlannedRoute planPreparedSelection(
            final RoutePlanningOrigin routeOrigin,
            final List<DeliveryRouteService.DeliveryStopInput> inputs
    ) {
        if (routeOrigin.hasCoordinates()) {
            return deliveryRouteService.planFromOrigin(
                    new DeliveryRouteService.RouteOriginInput(
                            routeOrigin.label(),
                            null,
                            routeOrigin.latitude(),
                            routeOrigin.longitude()
                    ),
                    inputs
            );
        }
        return deliveryRouteService.plan(routeOrigin.label(), inputs);
    }

    private String resolveOriginLabel(
            final String origem,
            final String fallback
    ) {
        return !isBlank(origem) ? origem.trim() : fallback;
    }

    private PreviewRouteView toPreviewRouteView(
            final DeliveryRouteService.PlannedRoute plannedRoute
    ) {
        return new PreviewRouteView(
                plannedRoute.origem(),
                plannedRoute.distanciaTotalKm(),
                plannedRoute.paradas().stream()
                        .map(this::toPreviewStopView)
                        .toList(),
                plannedRoute.mapaUrl()
        );
    }

    private PreviewStopView toPreviewStopView(
            final DeliveryRouteService.DeliveryStopPlan stop
    ) {
        return new PreviewStopView(stop);
    }

    private void validatePedidoCount(final List<Long> pedidoIds) {
        if (pedidoIds.size() < MIN_PEDIDOS_ROTA) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe ao menos 3 pedidos para roteirizar."
            );
        }
        if (pedidoIds.size() > MAX_PEDIDOS_ROTA) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Roteirizacao limitada a 10 pedidos por execucao."
            );
        }
    }

    private void validatePedidoForRouting(final PedidoEntity pedido) {
        if (pedido.getModoEntrega() == ModoEntrega.RETIRADA) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Pedido #" + pedido.getId()
                            + " esta marcado para retirada na loja e nao pode ser roteirizado."
            );
        }
        if (pedido.getStatus() == StatusPedido.CANCELADO
                || pedido.getStatus() == StatusPedido.ENTREGUE) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Pedido #" + pedido.getId()
                            + " nao esta elegivel para entrega."
            );
        }
        if (!ROUTABLE_ORDER_STATUSES.contains(pedido.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Pedido #" + pedido.getId()
                            + " precisa estar pronto para entrega ou sem rota ativa para roteirizar."
            );
        }
    }

    private void ensureOrdersNotInActiveRoutes(final List<Long> pedidoIds) {
        final List<Long> activeIds = entregaParadaRepository
                .findPedidoIdsInRouteStatuses(ACTIVE_ROUTE_STATUSES);
        final List<Long> blocked = pedidoIds.stream()
                .filter(activeIds::contains)
                .toList();
        if (!blocked.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pedidos ja vinculados a uma rota ativa: " + blocked
            );
        }
    }

    private EntregaParadaEntity toStopEntity(
            final EntregaRotaEntity route,
            final PedidoEntity pedido,
            final DeliveryRouteService.DeliveryStopPlan stop
    ) {
        final EntregaParadaEntity entity = new EntregaParadaEntity();
        entity.setRota(route);
        entity.setPedido(pedido);
        entity.setOrdem(stop.ordem());
        entity.setClienteNomeSnapshot(stop.clienteNome());
        entity.setEnderecoSnapshot(stop.enderecoEntrega());
        entity.setCodigoEntregaSnapshot(stop.codigoEntrega());
        entity.setStatus(EntregaParadaStatus.PENDENTE);
        entity.setDistanciaAnteriorKm(stop.distanciaAnteriorKm());
        entity.setDistanciaAcumuladaKm(stop.distanciaAcumuladaKm());
        entity.setDuracaoAnteriorSegundos(stop.duracaoAnteriorSegundos());
        entity.setDuracaoAcumuladaSegundos(stop.duracaoAcumuladaSegundos());
        if (stop.latitude() != null) {
            entity.setLatitude(BigDecimal.valueOf(stop.latitude()));
        }
        if (stop.longitude() != null) {
            entity.setLongitude(BigDecimal.valueOf(stop.longitude()));
        }
        return entity;
    }

    private Optional<EligibleOrderView> toEligibleOrderView(
            final PedidoEntity pedido
    ) {
        final String endereco = resolveEnderecoForListing(pedido);
        if (endereco.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new EligibleOrderView(
                pedido.getId(),
                formatPedidoNumero(pedido.getId()),
                resolveClienteNome(pedido),
                endereco,
                pedido.getTotal(),
                pedido.getStatus() != null ? pedido.getStatus().name() : STATUS_UNKNOWN,
                statusLabel(resolveDisplayStatus(pedido), pedido.getModoEntrega()),
                pedido.getCodigoEntrega(),
                pedido.getData(),
                NavigationLinkSupport.googleMapsDirections(endereco),
                NavigationLinkSupport.wazeNavigate(endereco)
        ));
    }

    private Optional<BaseDeliveryRequestView> toBaseDeliveryRequestView(
            final PedidoEntity pedido,
            final boolean emRota
    ) {
        final String endereco = resolveEnderecoForListing(pedido);
        if (endereco.isBlank()) {
            return Optional.empty();
        }
        final StatusPedido status = pedido.getStatus();
        final boolean elegivelParaRota = !emRota
                && status != null
                && ROUTABLE_ORDER_STATUSES.contains(status);
        return Optional.of(new BaseDeliveryRequestView(
                pedido.getId(),
                formatPedidoNumero(pedido.getId()),
                resolveClienteNome(pedido),
                endereco,
                pedido.getTotal(),
                status != null ? status.name() : STATUS_UNKNOWN,
                statusLabel(resolveDisplayStatus(pedido), pedido.getModoEntrega()),
                pedido.getCodigoEntrega(),
                pedido.getData(),
                NavigationLinkSupport.googleMapsDirections(endereco),
                NavigationLinkSupport.wazeNavigate(endereco),
                elegivelParaRota,
                emRota
        ));
    }

    private RouteSummaryView toRouteSummaryView(final EntregaRotaEntity route) {
        return new RouteSummaryView(
                route.getId(),
                route.getDataOperacao(),
                route.getOrigem(),
                route.getDistanciaTotalKm(),
                route.getMapaUrl(),
                route.getStatus().name(),
                routeStatusLabel(route.getStatus()),
                entregaParadaRepository.countByRotaId(route.getId()),
                route.getCreatedAt()
        );
    }

    private RouteDetailView toRouteDetailView(
            final EntregaRotaEntity route,
            final List<EntregaParadaEntity> stops
    ) {
        final RouteStopView nextStop = findNextStop(stops)
                .map(this::toRouteStopView)
                .orElse(null);
        return new RouteDetailView(
                route.getId(),
                route.getDataOperacao(),
                route.getOrigem(),
                route.getDistanciaTotalKm(),
                route.getCustoTotal(),
                route.getMapaUrl(),
                route.getStatus().name(),
                routeStatusLabel(route.getStatus()),
                route.getCreatedAt(),
                route.getEntregador() != null
                        ? route.getEntregador().getNome()
                        : DEFAULT_UNKNOWN,
                route.getIniciadaEm(),
                route.getFinalizadaEm(),
                route.getCriadaPor() != null
                        ? route.getCriadaPor().getNome()
                        : DEFAULT_UNKNOWN,
                buildRouteFinanceView(stops),
                nextStop,
                stops.stream().map(this::toRouteStopView).toList()
        );
    }

    private RouteFinanceView buildRouteFinanceView(
            final List<EntregaParadaEntity> stops
    ) {
        final RouteFinanceAccumulator accumulator = new RouteFinanceAccumulator();
        if (stops != null) {
            stops.stream()
                    .filter(AdminEntregaRouteService::nonNull)
                    .forEach(accumulator::include);
        }
        return accumulator.toView();
    }

    private DriverRouteView toDriverRouteView(
            final EntregaRotaEntity route,
            final List<EntregaParadaEntity> stops
    ) {
        final RouteStopView nextStop = findNextStop(stops)
                .map(this::toRouteStopView)
                .orElse(null);
        final long deliveredCount = stops.stream()
                .filter(stop -> stop.getStatus() == EntregaParadaStatus.ENTREGUE)
                .count();
        return new DriverRouteView(
                route.getId(),
                route.getStatus().name(),
                routeStatusLabel(route.getStatus()),
                route.getOrigem(),
                route.getMapaUrl(),
                stops.size(),
                deliveredCount,
                nextStop,
                route.getMotoristaLatitude(),
                route.getMotoristaLongitude(),
                route.getMotoristaLocalizacaoEm(),
                buildLiveMapUrl(route, nextStop),
                computeDistanceToStopKm(
                        route.getMotoristaLatitude(),
                        route.getMotoristaLongitude(),
                        nextStop != null ? stops.stream()
                                .filter(stop -> nextStop.id().equals(stop.getId()))
                                .findFirst()
                                .orElse(null) : null
                )
        );
    }

    private DriverStopDetailView toDriverStopDetailView(
            final EntregaRotaEntity route,
            final List<EntregaParadaEntity> stops,
            final EntregaParadaEntity selectedStop
    ) {
        final EntregaParadaEntity nextStop = findNextStop(stops).orElse(null);
        final boolean currentStop = nextStop != null
                && selectedStop != null
                && nextStop.getId().equals(selectedStop.getId());
        final PedidoEntity pedido = selectedStop != null ? selectedStop.getPedido() : null;
        return new DriverStopDetailView(
                toDriverRouteView(route, stops),
                selectedStop != null ? toRouteStopView(selectedStop) : null,
                pedido != null && pedido.getCliente() != null
                        ? defaultString(pedido.getCliente().getTelefone())
                        : "",
                pedido != null ? pedido.getTotal() : null,
                currentStop,
                route.getStatus() == EntregaRotaStatus.EM_EXECUCAO
                        && currentStop
                        && selectedStop != null
                        && selectedStop.getStatus() == EntregaParadaStatus.A_CAMINHO,
                route.getStatus() == EntregaRotaStatus.EM_EXECUCAO
                        && currentStop
                        && selectedStop != null
                        && selectedStop.getStatus() != EntregaParadaStatus.ENTREGUE
                        && selectedStop.getStatus() != EntregaParadaStatus.CANCELADA
                        && selectedStop.getStatus() != EntregaParadaStatus.TENTATIVA_SEM_SUCESSO
                        && selectedStop.getStatus() != EntregaParadaStatus.REAGENDAR,
                route.getStatus() == EntregaRotaStatus.EM_EXECUCAO
                        && currentStop
                        && selectedStop != null
                        && selectedStop.getStatus() == EntregaParadaStatus.CHEGOU,
                resolveDriverStopMessage(route, selectedStop, currentStop)
        );
    }

    private CustomerTrackingView toCustomerTrackingView(
            final Long pedidoId,
            final EntregaRotaEntity route,
            final List<EntregaParadaEntity> routeStops,
            final EntregaParadaEntity customerStop
    ) {
        if (route == null || routeStops == null || customerStop == null) {
            return CustomerTrackingView.unavailable(pedidoId);
        }
        final EntregaParadaEntity nextStop = findNextStop(routeStops).orElse(null);
        final boolean currentStop = nextStop != null
                && nextStop.getPedido() != null
                && pedidoId.equals(nextStop.getPedido().getId());
        final boolean arrived = currentStop
                && customerStop.getStatus() == EntregaParadaStatus.CHEGOU;
        final boolean approaching = currentStop && isApproaching(route, customerStop);
        final BigDecimal distanceToDeliveryKm = computeDistanceToStopKm(
                route.getMotoristaLatitude(),
                route.getMotoristaLongitude(),
                customerStop
        );
        final long deliveriesAhead = countActionableStopsBeforeCustomer(
                routeStops,
                customerStop
        );
        final Long etaMinutes = estimateCustomerEtaMinutes(
                route,
                routeStops,
                customerStop,
                nextStop,
                currentStop,
                arrived,
                distanceToDeliveryKm
        );
        final String etaLabel = buildCustomerEtaLabel(
                etaMinutes,
                arrived,
                approaching
        );
        final String mapsUrl = NavigationLinkSupport.googleMapsDirections(
                route.getMotoristaLatitude(),
                route.getMotoristaLongitude(),
                customerStop.getLatitude(),
                customerStop.getLongitude(),
                customerStop.getEnderecoSnapshot()
        );
        return new CustomerTrackingView(
                true,
                pedidoId,
                route.getId(),
                route.getStatus().name(),
                routeStatusLabel(route.getStatus()),
                buildCustomerTrackingMessage(
                        route,
                        nextStop,
                        currentStop,
                        approaching,
                        arrived,
                        deliveriesAhead,
                        etaLabel
                ),
                currentStop,
                approaching,
                arrived,
                deliveriesAhead,
                routeStops.stream()
                        .filter(stop -> stop.getStatus() != EntregaParadaStatus.ENTREGUE)
                        .filter(stop -> stop.getStatus() != EntregaParadaStatus.CANCELADA)
                        .count(),
                route.getMotoristaLatitude(),
                route.getMotoristaLongitude(),
                route.getMotoristaLocalizacaoEm(),
                buildCustomerMapUrl(route, customerStop),
                mapsUrl,
                NavigationLinkSupport.wazeNavigate(
                        customerStop.getLatitude(),
                        customerStop.getLongitude(),
                        customerStop.getEnderecoSnapshot()
                ),
                distanceToDeliveryKm,
                etaMinutes,
                etaLabel,
                customerStop.getEnderecoSnapshot()
        );
    }

    private RouteStopView toRouteStopView(final EntregaParadaEntity stop) {
        final PedidoEntity pedido = stop.getPedido();
        final String pagamentoSolicitado = pedido == null
                ? DEFAULT_UNKNOWN
                : resolveRequestedPaymentLabel(pedido);
        final String pagamentoRecebido = formatPaymentCategoryLabel(
                stop.getFormaPagamentoRecebida()
        );
        return new RouteStopView(
                stop.getId(),
                pedido != null ? pedido.getId() : null,
                formatPedidoNumero(pedido != null ? pedido.getId() : null),
                stop.getOrdem(),
                stop.getClienteNomeSnapshot(),
                stop.getEnderecoSnapshot(),
                stop.getCodigoEntregaSnapshot(),
                stop.getStatus().name(),
                stopStatusLabel(stop.getStatus()),
                stop.getDistanciaAnteriorKm(),
                stop.getDistanciaAcumuladaKm(),
                stop.getDuracaoAnteriorSegundos(),
                stop.getDuracaoAcumuladaSegundos(),
                stop.getConfirmadoEm(),
                NavigationLinkSupport.googleMapsDirections(
                        stop.getLatitude(),
                        stop.getLongitude(),
                        stop.getEnderecoSnapshot()
                ),
                NavigationLinkSupport.wazeNavigate(
                        stop.getLatitude(),
                        stop.getLongitude(),
                        stop.getEnderecoSnapshot()
                ),
                pagamentoSolicitado,
                pagamentoRecebido,
                Boolean.TRUE.equals(stop.getPagamentoDivergente()),
                stop.getAvaliacaoEntrega(),
                formatFailureReason(stop.getMotivoFalha()),
                formatOccurrences(stop.getOcorrencias()),
                stop.getObservacao(),
                pedido != null && isPaymentOnDelivery(pedido)
        );
    }

    private CustomerDeliveryIncidentView toCustomerDeliveryIncidentView(
            final EntregaParadaEntity stop
    ) {
        if (stop == null) {
            return CustomerDeliveryIncidentView.unavailable();
        }
        final EntregaParadaStatus status = stop.getStatus();
        final String statusLabel = stopStatusLabel(status);
        final String titulo = status == EntregaParadaStatus.REAGENDAR
                ? "Entrega reagendada"
                : "Tentativa de entrega sem sucesso";
        final String motivoFalha = formatFailureReason(stop.getMotivoFalha());
        final String mensagem = status == EntregaParadaStatus.REAGENDAR
                ? "Houve uma tentativa de entrega, mas o pedido voltou para reorganizacao de uma nova rota."
                : "Nao foi possivel concluir a ultima tentativa de entrega. Nossa equipe vai reorganizar o pedido.";
        return new CustomerDeliveryIncidentView(
                true,
                status != null ? status.name() : "",
                statusLabel,
                titulo,
                mensagem,
                motivoFalha,
                defaultString(stop.getObservacao()),
                stop.getConfirmadoEm()
        );
    }

    private Optional<EntregaParadaEntity> findNextStop(
            final List<EntregaParadaEntity> stops
    ) {
        if (stops == null || stops.isEmpty()) {
            return Optional.empty();
        }
        return stops.stream()
                .filter(stop -> stop.getStatus() != EntregaParadaStatus.ENTREGUE)
                .filter(stop -> stop.getStatus() != EntregaParadaStatus.CANCELADA)
                .filter(stop -> stop.getStatus() != EntregaParadaStatus.TENTATIVA_SEM_SUCESSO)
                .filter(stop -> stop.getStatus() != EntregaParadaStatus.REAGENDAR)
                .findFirst();
    }

    private void advanceRouteToNextActionableStop(
            final EntregaRotaEntity route,
            final List<EntregaParadaEntity> stops,
            final LocalDateTime now
    ) {
        final Optional<EntregaParadaEntity> nextPending = findNextStop(stops);
        if (nextPending.isPresent()) {
            if (nextPending.get().getStatus() == EntregaParadaStatus.PENDENTE) {
                nextPending.get().setStatus(EntregaParadaStatus.A_CAMINHO);
            }
            route.setStatus(EntregaRotaStatus.EM_EXECUCAO);
            return;
        }
        route.setStatus(EntregaRotaStatus.CONCLUIDA);
        route.setFinalizadaEm(now);
    }

    private EntregaParadaEntity findStopOrThrow(
            final List<EntregaParadaEntity> stops,
            final Long stopId
    ) {
        return stops.stream()
                .filter(item -> stopId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Parada nao encontrada."
                ));
    }

    private Optional<br.com.minimercadinho.saas.adapters.outbound.persistence.entity.AdminUserEntity> resolveActor(final String actorIdentity) {
        if (isBlank(actorIdentity)) {
            return Optional.empty();
        }
        return adminUserRepository.findByEmailIgnoreCase(actorIdentity.trim());
    }

    private String resolveEnderecoForListing(final PedidoEntity pedido) {
        final String orderAddress = normalizeAddress(pedido.getEnderecoEntrega());
        return orderAddress.isBlank() ? resolveEnderecoFromUsuario(pedido) : orderAddress;
    }

    private String resolveEnderecoFromUsuario(final PedidoEntity pedido) {
        if (pedido.getCliente() == null || isBlank(pedido.getCliente().getEmail())) {
            return "";
        }
        return customerRepository
                .findByEmailIgnoreCase(pedido.getCliente().getEmail())
                .map(CustomerEntity::getEndereco)
                .map(this::normalizeAddress)
                .orElse("");
    }

    private void notifyOrdersOutForDelivery(final List<PedidoEntity> pedidos) {
        final List<ClienteNotificacaoEntity> notifications = pedidos.stream()
                .map(this::buildOutForDeliveryNotification)
                .flatMap(Optional::stream)
                .toList();
        if (!notifications.isEmpty()) {
            notificacaoRepository.saveAll(notifications);
        }
    }

    private void maybeNotifyCustomerApproaching(
            final EntregaRotaEntity route,
            final EntregaParadaEntity nextStop
    ) {
        if (route == null
                || nextStop == null
                || nextStop.getPedido() == null
                || nextStop.getAproximandoNotificadoEm() != null
                || !isApproaching(route, nextStop)) {
            return;
        }
        buildApproachingNotification(nextStop.getPedido()).ifPresent(notification -> {
            nextStop.setAproximandoNotificadoEm(LocalDateTime.now());
            entregaParadaRepository.save(nextStop);
            notificacaoRepository.save(notification);
        });
    }

    private Optional<ClienteNotificacaoEntity> buildOutForDeliveryNotification(
            final PedidoEntity pedido
    ) {
        return resolveNotificationUser(pedido).map(usuario -> {
            final ClienteNotificacaoEntity notification =
                    new ClienteNotificacaoEntity();
            notification.setUsuario(usuario);
            notification.setTipo("PEDIDO");
            notification.setTitulo("Pedido saiu para entrega");
            notification.setMensagem(buildOutForDeliveryMessage(pedido));
            return notification;
        });
    }

    private Optional<ClienteNotificacaoEntity> buildApproachingNotification(
            final PedidoEntity pedido
    ) {
        return resolveNotificationUser(pedido).map(usuario -> {
            final ClienteNotificacaoEntity notification =
                    new ClienteNotificacaoEntity();
            notification.setUsuario(usuario);
            notification.setTipo(TYPE_ENTREGA);
            notification.setTitulo("Motoboy chegando");
            notification.setMensagem(buildApproachingMessage(pedido));
            return notification;
        });
    }

    private Optional<ClienteNotificacaoEntity> buildArrivedNotification(
            final PedidoEntity pedido
    ) {
        return resolveNotificationUser(pedido).map(usuario -> {
            final ClienteNotificacaoEntity notification =
                    new ClienteNotificacaoEntity();
            notification.setUsuario(usuario);
            notification.setTipo(TYPE_ENTREGA);
            notification.setTitulo("Motoboy chegou");
            notification.setMensagem(buildArrivedMessage(pedido));
            return notification;
        });
    }

    private Optional<ClienteNotificacaoEntity> buildFailureNotification(
            final PedidoEntity pedido,
            final EntregaParadaStatus failureStatus,
            final String failureReason
    ) {
        return resolveNotificationUser(pedido).map(usuario -> {
            final ClienteNotificacaoEntity notification =
                    new ClienteNotificacaoEntity();
            notification.setUsuario(usuario);
            notification.setTipo(TYPE_ENTREGA);
            notification.setTitulo(resolveFailureTitle(failureStatus));
            notification.setMensagem(buildFailureMessage(
                    pedido,
                    failureStatus,
                    failureReason
            ));
            return notification;
        });
    }

    private Optional<CustomerEntity> resolveNotificationUser(final PedidoEntity pedido) {
        if (pedido == null || pedido.getCliente() == null) {
            return Optional.empty();
        }
        final String email = pedido.getCliente().getEmail();
        if (!isBlank(email)) {
            final Optional<CustomerEntity> byEmail =
                    customerRepository.findByEmailOrCpf(email.trim());
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }
        final String cpf = pedido.getCliente().getCpf();
        if (!isBlank(cpf)) {
            return customerRepository.findByEmailOrCpf(cpf.trim());
        }
        return Optional.empty();
    }

    private boolean isPaymentOnDelivery(final PedidoEntity pedido) {
        if (pedido == null) {
            return false;
        }
        if (paymentMethodService.isOfflineValue(pedido.getMetodoPagamento())) {
            return true;
        }
        return pedido.getTipoPagamento() == TipoPagamento.DINHEIRO;
    }

    private StatusPedido resolveDisplayStatus(final PedidoEntity pedido) {
        if (pedido == null) {
            return null;
        }
        return isPaymentOnDelivery(pedido)
                && pedido.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO
                ? StatusPedido.ABERTO
                : pedido.getStatus();
    }

    private String resolveRequestedPaymentLabel(final PedidoEntity pedido) {
        final String resolved = paymentMethodService.resolveLabel(
                pedido.getMetodoPagamento()
        );
        if (!isBlank(resolved)) {
            return resolved;
        }
        if (pedido.getTipoPagamento() == null) {
            return DEFAULT_UNKNOWN;
        }
        return switch (pedido.getTipoPagamento()) {
            case PIX -> "Pix";
            case BOLETO -> "Boleto";
            case CARTAO_CREDITO -> "Cartao de credito";
            case CARTAO_DEBITO -> "Cartao de debito";
            case DINHEIRO -> "Dinheiro";
            case CUSTOM -> LABEL_OUTRO;
        };
    }

    private String normalizeReceivedPayment(final String rawValue) {
        if (isBlank(rawValue)) {
            return "";
        }
        final String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pix" -> "PIX";
            case "dinheiro", "cash" -> PAYMENT_DINHEIRO;
            case "cartao", "cartão", "credito", "crédito", "debito",
                    "débito", "cartao_credito", "cartao_debito",
                    "maquineta", "pos" -> PAYMENT_CARTAO;
            default -> PAYMENT_OUTRO;
        };
    }

    private String expectedPaymentCategory(final PedidoEntity pedido) {
        if (pedido == null) {
            return PAYMENT_OUTRO;
        }
        final String fromMethod = normalizeReceivedPayment(
                pedido.getMetodoPagamento()
        );
        if (!isBlank(fromMethod)) {
            return fromMethod;
        }
        if (pedido.getTipoPagamento() == null) {
            return PAYMENT_OUTRO;
        }
        return switch (pedido.getTipoPagamento()) {
            case PIX -> "PIX";
            case DINHEIRO -> PAYMENT_DINHEIRO;
            case CARTAO_CREDITO, CARTAO_DEBITO -> PAYMENT_CARTAO;
            default -> PAYMENT_OUTRO;
        };
    }

    private Integer sanitizeRating(final Integer rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue < 0) {
            return 0;
        }
        if (rawValue > 5) {
            return 5;
        }
        return rawValue;
    }

    private String joinOccurrences(final List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return "";
        }
        return rawValues.stream()
                .filter(value -> !isBlank(value))
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .map(value -> value.replace(' ', '_'))
                .distinct()
                .limit(8)
                .collect(Collectors.joining(","));
    }

    private EntregaParadaStatus resolveFailureStatus(final String rawValue) {
        if (isBlank(rawValue)) {
            return EntregaParadaStatus.TENTATIVA_SEM_SUCESSO;
        }
        return switch (rawValue.trim().toUpperCase(Locale.ROOT)) {
            case "REAGENDAR" -> EntregaParadaStatus.REAGENDAR;
            case "TENTATIVA_SEM_SUCESSO" -> EntregaParadaStatus.TENTATIVA_SEM_SUCESSO;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selecione um tipo de insucesso valido."
            );
        };
    }

    private String normalizeFailureReason(final String rawValue) {
        if (isBlank(rawValue)) {
            return "";
        }
        final String normalized = rawValue.trim().toUpperCase(Locale.ROOT)
                .replace(' ', '_');
        return switch (normalized) {
            case FAILURE_AUSENTE,
                    FAILURE_ENDERECO_RISCO,
                    FAILURE_RECUSA_PAGAMENTO,
                    "AGUARDAR_CONTATO",
                    PAYMENT_OUTRO -> normalized;
            default -> "";
        };
    }

    private String formatFailureReason(final String rawValue) {
        if (isBlank(rawValue)) {
            return "";
        }
        return switch (rawValue.trim().toUpperCase(Locale.ROOT)) {
            case FAILURE_AUSENTE -> "Cliente ausente";
            case FAILURE_ENDERECO_RISCO -> "Endereco de risco";
            case FAILURE_RECUSA_PAGAMENTO -> "Recusa de pagamento";
            case "AGUARDAR_CONTATO" -> "Aguardar contato";
            case PAYMENT_OUTRO -> LABEL_OUTRO;
            default -> rawValue.replace('_', ' ').trim();
        };
    }

    private String formatOccurrences(final String rawValue) {
        if (isBlank(rawValue)) {
            return "";
        }
        return List.of(rawValue.split(","))
                .stream()
                .filter(value -> !isBlank(value))
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .map(this::formatOccurrenceLabel)
                .collect(Collectors.joining(", "));
    }

    private String formatOccurrenceLabel(final String value) {
        return switch (value) {
            case "RACISMO" -> "Racismo";
            case "HOMOFOBIA" -> "Homofobia";
            case "TRANSFOBIA" -> "Transfobia";
            case "AGRESSAO" -> "Agressao";
            case "AMEACA" -> "Ameaca";
            case FAILURE_RECUSA_PAGAMENTO -> "Recusa de pagamento";
            case FAILURE_ENDERECO_RISCO -> "Endereco de risco";
            case FAILURE_AUSENTE -> "Cliente ausente";
            case PAYMENT_OUTRO -> LABEL_OUTRO;
            default -> value.replace('_', ' ').trim();
        };
    }

    private String normalizeObservation(final String rawValue) {
        if (isBlank(rawValue)) {
            return "";
        }
        final String normalized = rawValue.trim();
        return normalized.length() > 500
                ? normalized.substring(0, 500)
                : normalized;
    }

    private String formatPaymentCategoryLabel(final String category) {
        if (isBlank(category)) {
            return "";
        }
        return switch (category.trim().toUpperCase(Locale.ROOT)) {
            case "PIX" -> "Pix";
            case PAYMENT_DINHEIRO -> "Dinheiro";
            case PAYMENT_CARTAO -> "Cartao";
            default -> LABEL_OUTRO;
        };
    }

    private boolean isApproaching(
            final EntregaRotaEntity route,
            final EntregaParadaEntity stop
    ) {
        if (route == null || stop == null) {
            return false;
        }
        final BigDecimal distanceKm = computeDistanceToStopKm(
                route.getMotoristaLatitude(),
                route.getMotoristaLongitude(),
                stop
        );
        return distanceKm != null && distanceKm.compareTo(APPROACHING_DISTANCE_KM) <= 0;
    }

    private BigDecimal computeDistanceToStopKm(
            final BigDecimal currentLatitude,
            final BigDecimal currentLongitude,
            final EntregaParadaEntity stop
    ) {
        if (currentLatitude == null
                || currentLongitude == null
                || stop == null
                || stop.getLatitude() == null
                || stop.getLongitude() == null) {
            return null;
        }
        return haversineKm(
                currentLatitude.doubleValue(),
                currentLongitude.doubleValue(),
                stop.getLatitude().doubleValue(),
                stop.getLongitude().doubleValue()
        );
    }

    private long countActionableStopsBeforeCustomer(
            final List<EntregaParadaEntity> routeStops,
            final EntregaParadaEntity customerStop
    ) {
        if (routeStops == null || routeStops.isEmpty() || customerStop == null) {
            return 0;
        }
        return routeStops.stream()
                .filter(AdminEntregaRouteService::nonNull)
                .takeWhile(stop -> !isSameStop(stop, customerStop))
                .filter(this::isActionableStop)
                .count();
    }

    private Long estimateCustomerEtaMinutes(
            final EntregaRotaEntity route,
            final List<EntregaParadaEntity> routeStops,
            final EntregaParadaEntity customerStop,
            final EntregaParadaEntity nextStop,
            final boolean currentStop,
            final boolean arrived,
            final BigDecimal distanceToDeliveryKm
    ) {
        if (!canEstimateCustomerEta(route, customerStop)) {
            return null;
        }
        if (arrived) {
            return 0L;
        }
        if (currentStop) {
            return estimateCurrentStopEtaMinutes(
                    distanceToDeliveryKm,
                    customerStop
            );
        }
        return estimateQueuedStopEtaMinutes(
                routeStops,
                customerStop,
                nextStop
        );
    }

    private Long estimateLiveEtaMinutes(
            final BigDecimal distanceToDeliveryKm,
            final EntregaParadaEntity customerStop
    ) {
        if (distanceToDeliveryKm == null) {
            return null;
        }
        final BigDecimal speedKmh = resolveTrackingSpeedKmh(customerStop);
        final BigDecimal etaMinutes = distanceToDeliveryKm
                .multiply(BigDecimal.valueOf(60))
                .divide(speedKmh, 0, RoundingMode.UP);
        return Math.max(1L, etaMinutes.longValue());
    }

    private boolean canEstimateCustomerEta(
            final EntregaRotaEntity route,
            final EntregaParadaEntity customerStop
    ) {
        return route != null
                && route.getStatus() == EntregaRotaStatus.EM_EXECUCAO
                && customerStop != null;
    }

    private Long estimateCurrentStopEtaMinutes(
            final BigDecimal distanceToDeliveryKm,
            final EntregaParadaEntity customerStop
    ) {
        final Long liveEta = estimateLiveEtaMinutes(
                distanceToDeliveryKm,
                customerStop
        );
        return liveEta != null
                ? liveEta
                : minutesFromSeconds(customerStop.getDuracaoAnteriorSegundos());
    }

    private Long estimateQueuedStopEtaMinutes(
            final List<EntregaParadaEntity> routeStops,
            final EntregaParadaEntity customerStop,
            final EntregaParadaEntity nextStop
    ) {
        if (nextStop == null || routeStops == null || routeStops.isEmpty()) {
            return null;
        }
        final int nextStopIndex = findStopIndex(routeStops, nextStop);
        final int customerStopIndex = findStopIndex(routeStops, customerStop);
        if (nextStopIndex < 0 || customerStopIndex < nextStopIndex) {
            return null;
        }
        final long remainingSeconds = routeStops.subList(
                        nextStopIndex,
                        customerStopIndex + 1
                ).stream()
                .filter(this::isActionableStop)
                .mapToLong(this::durationSeconds)
                .sum();
        return minutesFromAccumulatedSeconds(remainingSeconds);
    }

    private int findStopIndex(
            final List<EntregaParadaEntity> routeStops,
            final EntregaParadaEntity targetStop
    ) {
        if (routeStops == null || targetStop == null) {
            return -1;
        }
        for (int index = 0; index < routeStops.size(); index++) {
            if (isSameStop(routeStops.get(index), targetStop)) {
                return index;
            }
        }
        return -1;
    }

    private long durationSeconds(final EntregaParadaEntity stop) {
        return stop != null && stop.getDuracaoAnteriorSegundos() != null
                ? stop.getDuracaoAnteriorSegundos()
                : 0L;
    }

    private boolean isSameStop(
            final EntregaParadaEntity firstStop,
            final EntregaParadaEntity secondStop
    ) {
        return firstStop != null
                && secondStop != null
                && secondStop.getId() != null
                && secondStop.getId().equals(firstStop.getId());
    }

    private BigDecimal resolveTrackingSpeedKmh(final EntregaParadaEntity customerStop) {
        if (customerStop == null
                || customerStop.getDistanciaAnteriorKm() == null
                || customerStop.getDuracaoAnteriorSegundos() == null
                || customerStop.getDuracaoAnteriorSegundos() <= 0) {
            return DEFAULT_TRACKING_SPEED_KMH;
        }
        final BigDecimal legSpeedKmh = customerStop.getDistanciaAnteriorKm()
                .multiply(BigDecimal.valueOf(3600))
                .divide(
                        BigDecimal.valueOf(customerStop.getDuracaoAnteriorSegundos()),
                        2,
                        RoundingMode.HALF_UP
                );
        return legSpeedKmh.compareTo(BigDecimal.ZERO) > 0
                ? legSpeedKmh
                : DEFAULT_TRACKING_SPEED_KMH;
    }

    private Long minutesFromSeconds(final Long seconds) {
        if (seconds == null || seconds <= 0) {
            return null;
        }
        return Math.max(1L, Math.ceilDiv(seconds, 60));
    }

    private Long minutesFromAccumulatedSeconds(final long seconds) {
        if (seconds <= 0) {
            return null;
        }
        return Math.max(1L, Math.ceilDiv(seconds, 60));
    }

    private boolean isActionableStop(final EntregaParadaEntity stop) {
        return stop != null
                && stop.getStatus() != EntregaParadaStatus.ENTREGUE
                && stop.getStatus() != EntregaParadaStatus.CANCELADA
                && stop.getStatus() != EntregaParadaStatus.TENTATIVA_SEM_SUCESSO
                && stop.getStatus() != EntregaParadaStatus.REAGENDAR;
    }

    private BigDecimal haversineKm(
            final double lat1,
            final double lon1,
            final double lat2,
            final double lon2
    ) {
        final double earthRadiusKm = 6371.0088d;
        final double latDistance = Math.toRadians(lat2 - lat1);
        final double lonDistance = Math.toRadians(lon2 - lon1);
        final double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        final double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(earthRadiusKm * c).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeCoordinate(
            final BigDecimal value,
            final int scale
    ) {
        if (value == null) {
            return null;
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    private String buildLiveMapUrl(
            final EntregaRotaEntity route,
            final RouteStopView nextStop
    ) {
        return NavigationLinkSupport.googleMapsEmbed(
                route.getMotoristaLatitude(),
                route.getMotoristaLongitude(),
                nextStop != null ? nextStop.enderecoEntrega() : route.getOrigem()
        );
    }

    private String buildCustomerMapUrl(
            final EntregaRotaEntity route,
            final EntregaParadaEntity customerStop
    ) {
        return NavigationLinkSupport.googleMapsEmbed(
                route.getMotoristaLatitude(),
                route.getMotoristaLongitude(),
                customerStop != null ? customerStop.getEnderecoSnapshot() : route.getOrigem()
        );
    }

    private String buildCustomerTrackingMessage(
            final EntregaRotaEntity route,
            final EntregaParadaEntity nextStop,
            final boolean currentStop,
            final boolean approaching,
            final boolean arrived,
            final long deliveriesAhead,
            final String etaLabel
    ) {
        if (route == null) {
            return "Rastreamento indisponivel no momento.";
        }
        if (route.getStatus() != EntregaRotaStatus.EM_EXECUCAO) {
            return "Sua entrega ja foi separada e aguarda a saida do motoboy.";
        }
        if (arrived || (currentStop
                && nextStop != null
                && nextStop.getStatus() == EntregaParadaStatus.CHEGOU)) {
            return "O motoboy chegou ao seu endereco. Tenha o codigo de confirmacao em maos.";
        }
        if (approaching) {
            return appendEta(
                    "O motoboy esta chegando ao seu endereco.",
                    etaLabel
            );
        }
        if (currentStop) {
            return appendEta(
                    "O motoboy esta a caminho do seu endereco.",
                    etaLabel
            );
        }
        if (nextStop != null) {
            final String message = deliveriesAhead == 1
                    ? "O motoboy esta em rota e finaliza 1 entrega antes da sua."
                    : "O motoboy esta em rota e finaliza "
                    + deliveriesAhead
                    + " entregas antes da sua.";
            return appendEta(message, etaLabel);
        }
        return "A rota esta em andamento.";
    }

    private String buildCustomerEtaLabel(
            final Long etaMinutes,
            final boolean arrived,
            final boolean approaching
    ) {
        if (arrived) {
            return "Motoboy no local";
        }
        if (etaMinutes == null) {
            return "";
        }
        if (approaching && etaMinutes <= 3) {
            return "Chegando agora";
        }
        if (etaMinutes <= 1) {
            return "Menos de 1 min";
        }
        return "Cerca de " + etaMinutes + " min";
    }

    private String appendEta(final String message, final String etaLabel) {
        if (isBlank(etaLabel)) {
            return message;
        }
        return message + " Previsao aproximada: " + etaLabel + ".";
    }

    private boolean matchesQuery(
            final EligibleOrderView item,
            final String rawQuery
    ) {
        if (isBlank(rawQuery)) {
            return true;
        }
        final String query = normalize(rawQuery);
        return normalize(item.numero()).contains(query)
                || normalize(item.clienteNome()).contains(query)
                || normalize(item.enderecoEntrega()).contains(query)
                || normalize(item.statusLabel()).contains(query);
    }

    private boolean matchesQuery(
            final BaseDeliveryRequestView item,
            final String rawQuery
    ) {
        if (isBlank(rawQuery)) {
            return true;
        }
        final String query = normalize(rawQuery);
        return normalize(item.numero()).contains(query)
                || normalize(item.clienteNome()).contains(query)
                || normalize(item.enderecoEntrega()).contains(query)
                || normalize(item.statusLabel()).contains(query)
                || normalize(item.elegivelParaRota() ? "pronto para entrega" : "")
                .contains(query)
                || normalize(item.emRota() ? "em rota" : "").contains(query);
    }

    private String normalizeAddress(final String value) {
        if (value == null) {
            return "";
        }
        final String normalized = value.trim();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private static String resolveClienteNome(final PedidoEntity pedido) {
        if (pedido.getCliente() != null && !isBlank(pedido.getCliente().getNome())) {
            return pedido.getCliente().getNome().trim();
        }
        return DEFAULT_LABEL;
    }

    private static String formatPedidoNumero(final Long pedidoId) {
        return pedidoId == null ? DEFAULT_UNKNOWN : String.format("%04d", pedidoId);
    }

    private static String buildOutForDeliveryMessage(final PedidoEntity pedido) {
        final StringBuilder message = new StringBuilder(
                "Seu pedido #" + formatPedidoNumero(pedido.getId())
                        + " saiu para entrega."
        );
        if (!isBlank(pedido.getCodigoEntrega())) {
            message.append(" Codigo de confirmacao: ")
                    .append(pedido.getCodigoEntrega())
                    .append('.');
        }
        return message.toString();
    }

    private static String buildApproachingMessage(final PedidoEntity pedido) {
        final StringBuilder message = new StringBuilder(
                "O motoboy esta chegando ao endereco do pedido #"
                        + formatPedidoNumero(pedido.getId())
                        + "."
        );
        if (!isBlank(pedido.getCodigoEntrega())) {
            message.append(" Tenha o codigo ")
                    .append(pedido.getCodigoEntrega())
                    .append(" em maos.");
        }
        return message.toString();
    }

    private static String buildArrivedMessage(final PedidoEntity pedido) {
        final StringBuilder message = new StringBuilder(
                "O motoboy chegou ao endereco do pedido #"
                        + formatPedidoNumero(pedido.getId())
                        + "."
        );
        if (!isBlank(pedido.getCodigoEntrega())) {
            message.append(" Tenha o codigo ")
                    .append(pedido.getCodigoEntrega())
                    .append(" para confirmar a entrega.");
        }
        return message.toString();
    }

    private String resolveFailureTitle(final EntregaParadaStatus failureStatus) {
        if (failureStatus == EntregaParadaStatus.REAGENDAR) {
            return "Entrega reagendada";
        }
        return "Tentativa de entrega sem sucesso";
    }

    private String buildFailureMessage(
            final PedidoEntity pedido,
            final EntregaParadaStatus failureStatus,
            final String failureReason
    ) {
        final StringBuilder message = new StringBuilder();
        if (failureStatus == EntregaParadaStatus.REAGENDAR) {
            message.append("A entrega do pedido #")
                    .append(formatPedidoNumero(pedido.getId()))
                    .append(" foi marcada para uma nova tentativa.");
        } else {
            message.append("Nao foi possivel concluir a entrega do pedido #")
                    .append(formatPedidoNumero(pedido.getId()))
                    .append(" nesta tentativa.");
        }
        final String formattedReason = formatFailureReason(failureReason);
        if (!formattedReason.isBlank()) {
            message.append(" Motivo registrado: ")
                    .append(formattedReason)
                    .append('.');
        }
        message.append(" Nossa equipe vai reorganizar a entrega.");
        return message.toString();
    }

    private String resolveDriverStopMessage(
            final EntregaRotaEntity route,
            final EntregaParadaEntity stop,
            final boolean currentStop
    ) {
        if (route == null || stop == null) {
            return "Parada indisponivel no momento.";
        }
        if (route.getStatus() == EntregaRotaStatus.PLANEJADA
                || route.getStatus() == EntregaRotaStatus.DESPACHADA) {
            return "Inicie a rota para liberar esta parada no painel do motoboy.";
        }
        if (route.getStatus() == EntregaRotaStatus.CONCLUIDA) {
            return "Essa rota ja foi concluida.";
        }
        if (stop.getStatus() == EntregaParadaStatus.ENTREGUE) {
            return "Entrega ja confirmada nessa parada.";
        }
        if (!currentStop) {
            return "Finalize a parada atual antes de avancar para este pedido.";
        }
        return switch (stop.getStatus()) {
            case A_CAMINHO ->
                    "Ao chegar no endereco, use \"Cheguei no local\" para liberar a confirmacao.";
            case CHEGOU ->
                    "Chegada registrada. Agora confirme a entrega e siga para a proxima parada.";
            case PENDENTE ->
                    "Essa parada ainda nao foi liberada para atendimento.";
            case TENTATIVA_SEM_SUCESSO ->
                    "Essa parada foi marcada como tentativa sem sucesso e voltou para reorganizacao.";
            case REAGENDAR ->
                    "Essa parada foi reagendada e voltou para reorganizacao.";
            case CANCELADA ->
                    "Essa parada foi cancelada.";
            case ENTREGUE ->
                    "Entrega ja confirmada nessa parada.";
        };
    }

    private String defaultString(final String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(final String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private static boolean nonNull(final Object value) {
        return value != null;
    }

    private static List<Long> uniqueIds(final List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private static String statusLabel(
            final StatusPedido status,
            final ModoEntrega modoEntrega
    ) {
        if (status == null) {
            return "Desconhecido";
        }
        return switch (status) {
            case ABERTO -> "Aberto";
            case AGUARDANDO_PAGAMENTO -> "Aguardando pagamento";
            case PAGO -> "Pago";
            case PRONTO_PARA_ENTREGA -> "Pronto para entrega";
            case PRONTO_PARA_RETIRADA -> "Pronto para retirada";
            case SAIU_PARA_ENTREGA, ENVIADO -> isDeliveryMode(modoEntrega)
                    ? "Saiu para entrega"
                    : "Enviado";
            case ENTREGUE -> "Entregue";
            case CANCELADO -> "Cancelado";
        };
    }

    private static boolean isDeliveryMode(final ModoEntrega modoEntrega) {
        return modoEntrega == null || modoEntrega == ModoEntrega.ENTREGA;
    }

    private static String routeStatusLabel(final EntregaRotaStatus status) {
        if (status == null) {
            return "Desconhecido";
        }
        return switch (status) {
            case RASCUNHO -> "Rascunho";
            case PLANEJADA -> "Planejada";
            case DESPACHADA -> "Despachada";
            case EM_EXECUCAO -> "Em execucao";
            case CONCLUIDA -> "Concluida";
            case CANCELADA -> "Cancelada";
        };
    }

    private static String stopStatusLabel(final EntregaParadaStatus status) {
        if (status == null) {
            return "Desconhecido";
        }
        return switch (status) {
            case PENDENTE -> "Pendente";
            case A_CAMINHO -> "A caminho";
            case CHEGOU -> "Chegou";
            case ENTREGUE -> "Entregue";
            case TENTATIVA_SEM_SUCESSO -> "Tentativa sem sucesso";
            case REAGENDAR -> "Reagendar";
            case CANCELADA -> "Cancelada";
        };
    }

    private record PlannedSelection(
            List<PedidoEntity> orderedPedidos,
            DeliveryRouteService.PlannedRoute plannedRoute
    ) {
    }

    private record PreparedSelectionData(
            List<PedidoEntity> orderedPedidos,
            List<DeliveryRouteService.DeliveryStopInput> inputs,
            boolean changed
    ) {
    }

    private record RoutePlanningOrigin(
            String label,
            Double latitude,
            Double longitude
    ) {
        private boolean hasCoordinates() {
            return latitude != null && longitude != null;
        }
    }

    private final class RouteFinanceAccumulator {

        private long cobrancasNoLocal;
        private long cobrancasConcluidas;
        private long divergenciasPagamento;
        private long paradasComInsucesso;
        private BigDecimal valorCobrarNaEntrega = BigDecimal.ZERO;
        private BigDecimal valorRecebidoConfirmado = BigDecimal.ZERO;
        private BigDecimal valorRecebidoPix = BigDecimal.ZERO;
        private BigDecimal valorRecebidoDinheiro = BigDecimal.ZERO;
        private BigDecimal valorRecebidoCartao = BigDecimal.ZERO;
        private BigDecimal valorRecebidoOutro = BigDecimal.ZERO;

        private void include(final EntregaParadaEntity stop) {
            if (isFailed(stop)) {
                paradasComInsucesso++;
            }
            final PedidoEntity pedido = stop.getPedido();
            if (pedido == null || !isPaymentOnDelivery(pedido)) {
                return;
            }
            includePayment(stop, pedido);
        }

        private boolean isFailed(final EntregaParadaEntity stop) {
            return stop.getStatus() == EntregaParadaStatus.REAGENDAR
                    || stop.getStatus() == EntregaParadaStatus.TENTATIVA_SEM_SUCESSO;
        }

        private void includePayment(
                final EntregaParadaEntity stop,
                final PedidoEntity pedido
        ) {
            final BigDecimal totalPedido = safeMoney(pedido.getTotal());
            cobrancasNoLocal++;
            valorCobrarNaEntrega = valorCobrarNaEntrega.add(totalPedido);

            if (Boolean.TRUE.equals(stop.getPagamentoDivergente())) {
                divergenciasPagamento++;
            }
            if (stop.getStatus() == EntregaParadaStatus.ENTREGUE) {
                includeReceivedAmount(stop, totalPedido);
            }
        }

        private void includeReceivedAmount(
                final EntregaParadaEntity stop,
                final BigDecimal totalPedido
        ) {
            cobrancasConcluidas++;
            valorRecebidoConfirmado = valorRecebidoConfirmado.add(totalPedido);
            switch (defaultString(stop.getFormaPagamentoRecebida())
                    .toUpperCase(Locale.ROOT)) {
                case "PIX" ->
                        valorRecebidoPix = valorRecebidoPix.add(totalPedido);
                case PAYMENT_DINHEIRO ->
                        valorRecebidoDinheiro = valorRecebidoDinheiro.add(totalPedido);
                case PAYMENT_CARTAO ->
                        valorRecebidoCartao = valorRecebidoCartao.add(totalPedido);
                default ->
                        valorRecebidoOutro = valorRecebidoOutro.add(totalPedido);
            }
        }

        private RouteFinanceView toView() {
            return new RouteFinanceView(
                    cobrancasNoLocal,
                    cobrancasConcluidas,
                    divergenciasPagamento,
                    paradasComInsucesso,
                    valorCobrarNaEntrega,
                    valorRecebidoConfirmado,
                    valorRecebidoPix,
                    valorRecebidoDinheiro,
                    valorRecebidoCartao,
                    valorRecebidoOutro
            );
        }

        private BigDecimal safeMoney(final BigDecimal value) {
            return value != null ? value : BigDecimal.ZERO;
        }
    }

    public record DashboardView(
            long pedidosElegiveis,
            long rotasAtivas,
            long rotasRecentes,
            long entreguesHoje
    ) {
    }

    public record EligibleOrderView(
            Long id,
            String numero,
            String clienteNome,
            String enderecoEntrega,
            BigDecimal total,
            String status,
            String statusLabel,
            String codigoEntrega,
            LocalDateTime data,
            String googleMapsUrl,
            String wazeUrl
    ) {
    }

    private record BaseDeliveryRequestView(
            Long id,
            String numero,
            String clienteNome,
            String enderecoEntrega,
            BigDecimal total,
            String status,
            String statusLabel,
            String codigoEntrega,
            LocalDateTime data,
            String googleMapsUrl,
            String wazeUrl,
            boolean elegivelParaRota,
            boolean emRota
    ) {
    }

    public record DeliveryRequestView(
            Long id,
            String numero,
            String clienteNome,
            String enderecoEntrega,
            BigDecimal total,
            String status,
            String statusLabel,
            String codigoEntrega,
            LocalDateTime data,
            String googleMapsUrl,
            String wazeUrl,
            String referenciaAnterior,
            BigDecimal distanciaAnteriorKm,
            boolean elegivelParaRota,
            boolean emRota
    ) {
    }

    public record RouteSummaryView(
            Long id,
            LocalDate dataOperacao,
            String origem,
            BigDecimal distanciaTotalKm,
            String mapaUrl,
            String status,
            String statusLabel,
            long totalParadas,
            LocalDateTime createdAt
    ) {
    }

    public record RouteStopView(
            Long id,
            Long pedidoId,
            String pedidoNumero,
            Integer ordem,
            String clienteNome,
            String enderecoEntrega,
            String codigoEntrega,
            String status,
            String statusLabel,
            BigDecimal distanciaAnteriorKm,
            BigDecimal distanciaAcumuladaKm,
            Long duracaoAnteriorSegundos,
            Long duracaoAcumuladaSegundos,
            LocalDateTime confirmadoEm,
            String googleMapsUrl,
            String wazeUrl,
            String pagamentoSolicitado,
            String pagamentoRecebido,
            boolean pagamentoDivergente,
            Integer avaliacaoEntrega,
            String motivoFalha,
            String ocorrencias,
            String observacao,
            boolean requerPagamentoNoLocal
    ) {
    }

    public record RouteDetailView(
            Long id,
            LocalDate dataOperacao,
            String origem,
            BigDecimal distanciaTotalKm,
            BigDecimal custoTotal,
            String mapaUrl,
            String status,
            String statusLabel,
            LocalDateTime createdAt,
            String entregadorNome,
            LocalDateTime iniciadaEm,
            LocalDateTime finalizadaEm,
            String criadaPorNome,
            RouteFinanceView financeiro,
            RouteStopView proximaParada,
            List<RouteStopView> paradas
    ) {
    }

    public record RouteFinanceView(
            long cobrancasNoLocal,
            long cobrancasConcluidas,
            long divergenciasPagamento,
            long paradasComInsucesso,
            BigDecimal valorCobrarNaEntrega,
            BigDecimal valorRecebidoConfirmado,
            BigDecimal valorRecebidoPix,
            BigDecimal valorRecebidoDinheiro,
            BigDecimal valorRecebidoCartao,
            BigDecimal valorRecebidoOutro
    ) {
    }

    public record DriverRouteView(
            Long id,
            String status,
            String statusLabel,
            String origem,
            String mapaUrl,
            long totalParadas,
            long entregues,
            RouteStopView proximaParada,
            BigDecimal motoristaLatitude,
            BigDecimal motoristaLongitude,
            LocalDateTime motoristaLocalizacaoEm,
            String mapaAoVivoUrl,
            BigDecimal distanciaAteProximaParadaKm
    ) {
    }

    public record DriverStopDetailView(
            DriverRouteView rota,
            RouteStopView parada,
            String telefoneCliente,
            BigDecimal totalPedido,
            boolean paradaAtual,
            boolean podeRegistrarChegada,
            boolean podeRegistrarInsucesso,
            boolean podeConfirmarEntrega,
            String mensagemOperacional
    ) {
    }

    public record CustomerTrackingView(
            boolean disponivel,
            Long pedidoId,
            Long rotaId,
            String statusRota,
            String statusRotaLabel,
            String mensagem,
            boolean pedidoEhProximaParada,
            boolean aproximando,
            boolean motoboyChegou,
            long entregasAntesDaSua,
            long paradasRestantes,
            BigDecimal motoristaLatitude,
            BigDecimal motoristaLongitude,
            LocalDateTime motoristaLocalizacaoEm,
            String mapaAoVivoUrl,
            String googleMapsUrl,
            String wazeUrl,
            BigDecimal distanciaAteEntregaKm,
            Long etaMinutos,
            String etaLabel,
            String enderecoEntrega
    ) {
        public static CustomerTrackingView unavailable(final Long pedidoId) {
            return new CustomerTrackingView(
                    false,
                    pedidoId,
                    null,
                    "",
                    "",
                    "Rastreamento indisponivel no momento.",
                    false,
                    false,
                    false,
                    0,
                    0,
                    null,
                    null,
                    null,
                    "",
                    "",
                    "",
                    null,
                    null,
                    "",
                    ""
            );
        }
    }

    public record CustomerDeliveryIncidentView(
            boolean disponivel,
            String status,
            String statusLabel,
            String titulo,
            String mensagem,
            String motivoFalha,
            String observacao,
            LocalDateTime ocorridoEm
    ) {
        public static CustomerDeliveryIncidentView unavailable() {
            return new CustomerDeliveryIncidentView(
                    false,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    null
            );
        }
    }

    public record DeliveryClosureInput(
            String formaPagamentoRecebida,
            Integer avaliacaoEntrega,
            List<String> ocorrencias,
            String observacao
    ) {
    }

    public record DeliveryFailureInput(
            String statusFalha,
            String motivoFalha,
            String observacao
    ) {
    }

    public static final class PreviewRouteView {

        private final String origem;
        private final BigDecimal distanciaTotalKm;
        private final List<PreviewStopView> paradas;
        private final String mapaUrl;

        public PreviewRouteView(
                final String origemValue,
                final BigDecimal distanciaTotalKmValue,
                final List<PreviewStopView> paradasValue,
                final String mapaUrlValue
        ) {
            this.origem = origemValue;
            this.distanciaTotalKm = distanciaTotalKmValue;
            this.paradas = paradasValue == null ? List.of() : List.copyOf(paradasValue);
            this.mapaUrl = mapaUrlValue;
        }

        public String getOrigem() {
            return origem;
        }

        public BigDecimal getDistanciaTotalKm() {
            return distanciaTotalKm;
        }

        public List<PreviewStopView> getParadas() {
            return paradas;
        }

        public String getMapaUrl() {
            return mapaUrl;
        }
    }

    public static final class PreviewStopView {

        private final Integer ordem;
        private final Long pedidoId;
        private final String clienteNome;
        private final String enderecoEntrega;
        private final String codigoEntrega;
        private final String status;
        private final BigDecimal distanciaAnteriorKm;
        private final BigDecimal distanciaAcumuladaKm;
        private final Double latitude;
        private final Double longitude;
        private final Long duracaoAnteriorSegundos;
        private final Long duracaoAcumuladaSegundos;

        public PreviewStopView(final DeliveryRouteService.DeliveryStopPlan stop) {
            this.ordem = stop.ordem();
            this.pedidoId = stop.pedidoId();
            this.clienteNome = stop.clienteNome();
            this.enderecoEntrega = stop.enderecoEntrega();
            this.codigoEntrega = stop.codigoEntrega();
            this.status = stop.status();
            this.distanciaAnteriorKm = stop.distanciaAnteriorKm();
            this.distanciaAcumuladaKm = stop.distanciaAcumuladaKm();
            this.latitude = stop.latitude();
            this.longitude = stop.longitude();
            this.duracaoAnteriorSegundos = stop.duracaoAnteriorSegundos();
            this.duracaoAcumuladaSegundos = stop.duracaoAcumuladaSegundos();
        }

        public Integer getOrdem() {
            return ordem;
        }

        public Long getPedidoId() {
            return pedidoId;
        }

        public String getClienteNome() {
            return clienteNome;
        }

        public String getEnderecoEntrega() {
            return enderecoEntrega;
        }

        public String getCodigoEntrega() {
            return codigoEntrega;
        }

        public String getStatus() {
            return status;
        }

        public BigDecimal getDistanciaAnteriorKm() {
            return distanciaAnteriorKm;
        }

        public BigDecimal getDistanciaAcumuladaKm() {
            return distanciaAcumuladaKm;
        }

        public Double getLatitude() {
            return latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public Long getDuracaoAnteriorSegundos() {
            return duracaoAnteriorSegundos;
        }

        public Long getDuracaoAcumuladaSegundos() {
            return duracaoAcumuladaSegundos;
        }
    }
}
