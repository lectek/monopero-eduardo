package br.com.minimercadinho.saas.application.service.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeliveryRouteService {

    private static final Logger log = LoggerFactory.getLogger(
            DeliveryRouteService.class
    );

    private static final int DISTANCE_SCALE = 2;
    private static final long GEOCODE_RATE_LIMIT_COOLDOWN_MS = 60_000L;
    private static final String ROUTE_ENGINE_GEO = "geo";
    private static final Pattern INLINE_COMPLEMENT_AFTER_NUMBER = Pattern.compile(
            "(?iu)(\\b\\d+[\\p{L}\\d/-]*)\\s*[-/]\\s*"
                    + "(?:loja|lj|sala|sl|ap(?:to|artamento)?|bloco|box|cj|conjunto|andar|fundos|"
                    + "galp[aã]o|quadra|qd|lote|lt)\\b.*$"
    );
    private static final Pattern LEADING_COMPLEMENT_SEGMENT = Pattern.compile(
            "(?iu)^(?:loja|lj|sala|sl|ap(?:to|artamento)?|bloco|box|cj|conjunto|andar|fundos|"
                    + "galp[aã]o|quadra|qd|lote|lt)\\b.*$"
    );
    private static final Pattern CEP_SEGMENT = Pattern.compile("^\\d{5}-?\\d{3}$");
    private static final Pattern BRAZIL_SEGMENT = Pattern.compile("(?iu)\\bbrasil\\b");
    private static final String STORE_STREET_ABBREVIATED =
            "Rua Prefeito Luiz A. M. Coutinho";
    private static final String STORE_STREET_ABBREVIATED_NO_DOTS =
            "Rua Prefeito Luiz A M Coutinho";
    private static final String STORE_STREET_CANONICAL =
            "Rua Prefeito Luiz Alberto M Coutinho";
    private static final Pattern ADDRESS_NUMBER_MARKER = Pattern.compile(
            "(?iu)\\b(?:n(?:u|ú)?m(?:ero)?|n[º°o]?|num|numero|nro|nr)\\s*\\d+[\\p{L}\\d/-]*\\b"
    );
    private static final Pattern TRAILING_HOUSE_NUMBER = Pattern.compile(
            "(?iu)\\s+\\d+[\\p{L}\\d/-]*$"
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String nominatimBaseUrl;
    private final String osrmBaseUrl;
    private final String routeEngine;
    private final String userAgent;
    private final Duration timeout;
    private final Map<String, GeoPoint> sharedGeocodeCache = new ConcurrentHashMap<>();
    private volatile long geocodeRateLimitedUntilMs;

    public DeliveryRouteService(
            final ObjectMapper objectMapperValue,
            @Value("${app.route.nominatim.base-url:https://nominatim.openstreetmap.org/search}")
            final String nominatimBaseUrlValue,
            @Value("${app.route.osrm.base-url:http://localhost:5000}")
            final String osrmBaseUrlValue,
            @Value("${app.route.engine:auto}")
            final String routeEngineValue,
            @Value("${app.route.nominatim.user-agent:HotelPet/1.0}")
            final String userAgentValue,
            @Value("${app.route.nominatim.timeout-ms:6000}")
            final long timeoutMs
    ) {
        this.objectMapper = objectMapperValue;
        this.nominatimBaseUrl = nominatimBaseUrlValue;
        this.osrmBaseUrl = trimTrailingSlash(osrmBaseUrlValue);
        this.routeEngine = normalize(routeEngineValue);
        this.userAgent = userAgentValue;
        this.timeout = Duration.ofMillis(Math.max(timeoutMs, 1000L));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    public PlannedRoute plan(
            final String origemEndereco,
            final List<DeliveryStopInput> stops
    ) {
        return planFromOrigin(
                new RouteOriginInput(origemEndereco, origemEndereco, null, null),
                stops
        );
    }

    public PlannedRoute planFromOrigin(
            final RouteOriginInput origemInput,
            final List<DeliveryStopInput> stops
    ) {
        if (stops == null || stops.isEmpty()) {
            throw new IllegalArgumentException(
                    "Informe ao menos um pedido para roteirizacao."
            );
        }

        final Map<String, GeoPoint> cache = new HashMap<>();
        final ResolvedOrigin origemResolvida;
        final GeoPoint origem;
        final List<ResolvedStop> resolved = new ArrayList<>(stops.size());
        try {
            origemResolvida = resolveOrigin(origemInput, cache);
            origem = origemResolvida.point();
            for (DeliveryStopInput stop : stops) {
                if (stop == null || stop.pedidoId() == null) {
                    throw new IllegalArgumentException("Pedido invalido na rota.");
                }
                final String endereco = normalizeAddress(stop.enderecoEntrega());
                if (endereco.isBlank()) {
                    throw new IllegalArgumentException(
                            "Pedido #" + stop.pedidoId()
                            + " sem endereco de entrega."
                    );
                }
                resolved.add(new ResolvedStop(stop, geocodeCached(endereco, cache)));
            }
        } catch (GeocodeRateLimitException ex) {
            log.warn(
                    "Geocodificacao temporariamente limitada. Aplicando fallback sem coordenadas: {}",
                    ex.getMessage()
            );
            return buildFallbackRoute(origemInput, stops);
        }

        final MatrixData matrix = resolveMatrix(origem, resolved);
        final DeliveryRouteOptimizer.RouteOrder best =
                DeliveryRouteOptimizer.optimize(matrix.travelCosts());
        final List<DeliveryStopPlan> plans = new ArrayList<>(resolved.size());
        final List<GeoPoint> orderedPoints = new ArrayList<>(resolved.size());

        double acumulada = 0.0d;
        long duracaoAcumuladaSegundos = 0L;
        int anteriorIndex = 0;
        for (int i = 0; i < best.stopIndexes().length; i++) {
            final int stopIndex = best.stopIndexes()[i];
            final int matrixIndex = stopIndex + 1;
            final ResolvedStop stop = resolved.get(stopIndex);
            final double trecho = matrix.distanceKm()[anteriorIndex][matrixIndex];
            final long duracaoTrecho = matrix.durationSeconds()[anteriorIndex][matrixIndex];
            acumulada += trecho;
            if (duracaoTrecho >= 0L) {
                duracaoAcumuladaSegundos += duracaoTrecho;
            }
            orderedPoints.add(stop.point());
            plans.add(
                    new DeliveryStopPlan(
                            i + 1,
                            stop.input().pedidoId(),
                            defaultString(stop.input().clienteNome(), "Cliente"),
                            stop.input().enderecoEntrega(),
                            stop.input().codigoEntrega(),
                            stop.input().status(),
                            toScale(trecho),
                            toScale(acumulada),
                            stop.point().lat(),
                            stop.point().lon(),
                            duracaoTrecho >= 0L ? duracaoTrecho : null,
                            duracaoTrecho >= 0L ? duracaoAcumuladaSegundos : null
                    )
            );
            anteriorIndex = matrixIndex;
        }

        final String mapsUrl = buildGoogleMapsUrl(origem, orderedPoints);
        return new PlannedRoute(
                origemResolvida.displayLabel(),
                toScale(best.totalCost()),
                toScale(acumulada),
                plans,
                mapsUrl
        );
    }

    public List<LegDistanceEstimate> estimateSequentialDistances(
            final String origemEndereco,
            final List<String> destinos
    ) {
        final List<String> orderedDestinations = destinos == null
                ? List.of()
                : destinos;
        if (orderedDestinations.isEmpty()) {
            return List.of();
        }

        final Map<String, GeoPoint> cache = new HashMap<>();
        GeoPoint previousPoint = null;
        String previousLabel = "Base";

        final String origin = normalizeAddress(origemEndereco);
        if (!origin.isBlank()) {
            try {
                previousPoint = geocodeCached(origin, cache);
            } catch (RuntimeException ex) {
                log.warn("Falha ao geocodificar origem da rota para estimativa: {}", ex.getMessage());
            }
        }

        final List<LegDistanceEstimate> estimates = new ArrayList<>(
                orderedDestinations.size()
        );
        for (String destinationRaw : orderedDestinations) {
            final String destination = normalizeAddress(destinationRaw);
            if (destination.isBlank()) {
                estimates.add(new LegDistanceEstimate(previousLabel, null));
                continue;
            }
            try {
                final GeoPoint currentPoint = geocodeCached(destination, cache);
                final BigDecimal distance = previousPoint == null
                        ? null
                        : toScale(distanceKm(previousPoint, currentPoint));
                estimates.add(new LegDistanceEstimate(previousLabel, distance));
                previousPoint = currentPoint;
                previousLabel = destination;
            } catch (RuntimeException ex) {
                log.warn(
                        "Falha ao estimar distancia sequencial para '{}': {}",
                        destination,
                        ex.getMessage()
                );
                estimates.add(new LegDistanceEstimate(previousLabel, null));
            }
        }
        return estimates;
    }

    private ResolvedOrigin resolveOrigin(
            final RouteOriginInput origemInput,
            final Map<String, GeoPoint> cache
    ) {
        if (origemInput != null && origemInput.hasCoordinates()) {
            final String displayLabel = defaultString(
                    origemInput.displayLabel(),
                    "Localizacao atual do dispositivo"
            );
            return new ResolvedOrigin(
                    displayLabel,
                    new GeoPoint(origemInput.latitude(), origemInput.longitude())
            );
        }

        final String origemNormalizada = normalizeAddress(
                origemInput != null ? origemInput.endereco() : null
        );
        if (origemNormalizada.isBlank()) {
            throw new IllegalArgumentException(
                    "Endereco de origem nao informado."
            );
        }
        final String displayLabel = defaultString(
                origemInput != null ? origemInput.displayLabel() : null,
                origemNormalizada
        );
        return new ResolvedOrigin(
                displayLabel,
                geocodeCached(origemNormalizada, cache)
        );
    }

    public BigDecimal estimateDistanceBetween(
            final String origemEndereco,
            final String destinoEndereco
    ) {
        final List<LegDistanceEstimate> estimates = estimateSequentialDistances(
                origemEndereco,
                List.of(destinoEndereco)
        );
        if (estimates.isEmpty()) {
            return null;
        }
        return estimates.getFirst().distanciaKm();
    }

    private GeoPoint geocodeCached(
            final String endereco,
            final Map<String, GeoPoint> cache
    ) {
        final GeoPoint cached = cache.get(endereco);
        if (cached != null) {
            return cached;
        }
        final GeoPoint shared = sharedGeocodeCache.get(endereco);
        if (shared != null) {
            cache.put(endereco, shared);
            return shared;
        }
        final GeoPoint point = geocode(endereco);
        sharedGeocodeCache.put(endereco, point);
        cache.put(endereco, point);
        return point;
    }

    private GeoPoint geocode(final String endereco) {
        RuntimeException lastFailure = null;
        for (String candidate : geocodeCandidates(endereco)) {
            try {
                final GeoPoint point = geocodeCandidate(candidate);
                if (!candidate.equals(endereco)) {
                    log.info(
                            "Geocodificacao por rua aplicada para '{}' usando '{}'.",
                            endereco,
                            candidate
                    );
                }
                return point;
            } catch (GeocodeRateLimitException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                lastFailure = ex;
            }
        }
        if (lastFailure != null) {
            log.warn(
                    "Falha ao geocodificar endereco '{}': {}",
                    endereco,
                    lastFailure.getMessage()
            );
            throw lastFailure;
        }
        throw new IllegalStateException(
                "Endereco nao encontrado para roteirizacao: " + endereco
        );
    }

    private GeoPoint geocodeCandidate(final String endereco) {
        final long now = System.currentTimeMillis();
        if (now < geocodeRateLimitedUntilMs) {
            throw new GeocodeRateLimitException(
                    "Servico de geocodificacao temporariamente limitado."
            );
        }
        try {
            final String query = URLEncoder.encode(
                    endereco,
                    StandardCharsets.UTF_8
            );
            final URI uri = URI.create(
                    nominatimBaseUrl
                    + "?format=json&countrycodes=br&limit=1&q="
                    + query
            );
            final HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent)
                    .timeout(timeout)
                    .GET()
                    .build();
            final HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() == 429) {
                geocodeRateLimitedUntilMs =
                        System.currentTimeMillis() + GEOCODE_RATE_LIMIT_COOLDOWN_MS;
                throw new GeocodeRateLimitException(
                        "Falha ao geocodificar endereco: HTTP 429"
                );
            }
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "Falha ao geocodificar endereco: HTTP "
                        + response.statusCode()
                );
            }
            return parseFirstPoint(response.body(), endereco);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Geocodificacao interrompida para endereco: " + endereco,
                    ex
            );
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Falha de comunicacao na geocodificacao: " + endereco,
                    ex
            );
        }
    }

    private PlannedRoute buildFallbackRoute(
            final RouteOriginInput origemInput,
            final List<DeliveryStopInput> stops
    ) {
        final String origem = resolveFallbackOriginLabel(origemInput);
        final List<DeliveryStopPlan> plans = new ArrayList<>(stops.size());
        for (int index = 0; index < stops.size(); index++) {
            final DeliveryStopInput stop = stops.get(index);
            plans.add(new DeliveryStopPlan(
                    index + 1,
                    stop.pedidoId(),
                    defaultString(stop.clienteNome(), "Cliente"),
                    stop.enderecoEntrega(),
                    stop.codigoEntrega(),
                    stop.status(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }
        return new PlannedRoute(
                origem,
                null,
                null,
                plans,
                buildGoogleMapsAddressUrl(origemInput, stops)
        );
    }

    private String resolveFallbackOriginLabel(final RouteOriginInput origemInput) {
        if (origemInput == null) {
            return "Origem da rota";
        }
        if (origemInput.hasCoordinates()) {
            return defaultString(
                    origemInput.displayLabel(),
                    "Localizacao atual do dispositivo"
            );
        }
        return defaultString(origemInput.endereco(), "Origem da rota");
    }

    private GeoPoint parseFirstPoint(final String responseBody,
                                     final String endereco) throws IOException {
        final JsonNode root = objectMapper.readTree(responseBody);
        if (!root.isArray() || root.isEmpty()) {
            throw new IllegalStateException(
                    "Endereco nao encontrado para roteirizacao: " + endereco
            );
        }
        final JsonNode first = root.get(0);
        final double lat = parseCoordinate(first.path("lat").asText(""));
        final double lon = parseCoordinate(first.path("lon").asText(""));
        if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
            throw new IllegalStateException(
                    "Coordenadas invalidas para endereco: " + endereco
            );
        }
        return new GeoPoint(lat, lon);
    }

    private static double parseCoordinate(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private MatrixData resolveMatrix(
            final GeoPoint origem,
            final List<ResolvedStop> stops
    ) {
        if (!ROUTE_ENGINE_GEO.equals(routeEngine)) {
            try {
                return buildRoadMatrix(origem, stops);
            } catch (RuntimeException ex) {
                log.warn("Falha ao obter matriz OSRM. Fallback geometrico ativado: {}", ex.getMessage());
            }
        }
        return buildGeoMatrix(origem, stops);
    }

    private MatrixData buildRoadMatrix(
            final GeoPoint origem,
            final List<ResolvedStop> stops
    ) {
        final List<GeoPoint> points = new ArrayList<>(stops.size() + 1);
        points.add(origem);
        for (ResolvedStop stop : stops) {
            points.add(stop.point());
        }
        final String coordinates = points.stream()
                .map(this::formatOsrmCoordinate)
                .reduce((left, right) -> left + ";" + right)
                .orElseThrow();
        final URI uri = URI.create(
                osrmBaseUrl
                        + "/table/v1/driving/"
                        + coordinates
                        + "?annotations=distance,duration"
        );
        final JsonNode root = sendJsonRequest(uri);
        if (!"Ok".equalsIgnoreCase(root.path("code").asText(""))) {
            throw new IllegalStateException("OSRM retornou erro ao montar matriz.");
        }

        final int size = points.size();
        final JsonNode distancesNode = root.path("distances");
        final JsonNode durationsNode = root.path("durations");
        if (!distancesNode.isArray() || distancesNode.size() != size) {
            throw new IllegalStateException("Matriz de distancias invalida no OSRM.");
        }
        if (!durationsNode.isArray() || durationsNode.size() != size) {
            throw new IllegalStateException("Matriz de duracoes invalida no OSRM.");
        }

        final double[][] distancesKm = new double[size][size];
        final long[][] durationsSeconds = new long[size][size];
        final double[][] travelCosts = new double[size][size];
        for (int row = 0; row < size; row++) {
            final JsonNode distanceRow = distancesNode.get(row);
            final JsonNode durationRow = durationsNode.get(row);
            if (!distanceRow.isArray() || distanceRow.size() != size
                    || !durationRow.isArray() || durationRow.size() != size) {
                throw new IllegalStateException("Linha invalida da matriz OSRM.");
            }
            for (int col = 0; col < size; col++) {
                if (distanceRow.get(col).isNull() || durationRow.get(col).isNull()) {
                    throw new IllegalStateException(
                            "OSRM nao conseguiu calcular a matriz completa da rota."
                    );
                }
                final double distanceMeters = distanceRow.get(col).asDouble();
                final double durationValue = durationRow.get(col).asDouble();
                distancesKm[row][col] = Math.max(0.0d, distanceMeters / 1000.0d);
                durationsSeconds[row][col] = Math.max(0L, Math.round(durationValue));
                travelCosts[row][col] = durationValue;
            }
        }
        return new MatrixData(travelCosts, distancesKm, durationsSeconds);
    }

    private MatrixData buildGeoMatrix(
            final GeoPoint origem,
            final List<ResolvedStop> stops
    ) {
        final int size = stops.size() + 1;
        final GeoPoint[] points = new GeoPoint[size];
        points[0] = origem;
        for (int index = 0; index < stops.size(); index++) {
            points[index + 1] = stops.get(index).point();
        }

        final double[][] travelCosts = new double[size][size];
        final double[][] distancesKm = new double[size][size];
        final long[][] durationsSeconds = new long[size][size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (row == col) {
                    distancesKm[row][col] = 0.0d;
                    durationsSeconds[row][col] = 0L;
                    travelCosts[row][col] = 0.0d;
                    continue;
                }
                final double distance = distanceKm(points[row], points[col]);
                distancesKm[row][col] = distance;
                durationsSeconds[row][col] = -1L;
                travelCosts[row][col] = distance;
            }
        }
        return new MatrixData(travelCosts, distancesKm, durationsSeconds);
    }

    private JsonNode sendJsonRequest(final URI uri) {
        final HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .timeout(timeout)
                .GET()
                .build();
        try {
            final HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "Falha HTTP " + response.statusCode() + " para " + uri
                );
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Requisicao interrompida para " + uri, ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha de comunicacao para " + uri, ex);
        }
    }

    private static double distanceKm(final GeoPoint a, final GeoPoint b) {
        final double earthRadiusKm = 6371.0088d;
        final double dLat = Math.toRadians(b.lat() - a.lat());
        final double dLon = Math.toRadians(b.lon() - a.lon());
        final double lat1 = Math.toRadians(a.lat());
        final double lat2 = Math.toRadians(b.lat());

        final double sinDlat = Math.sin(dLat / 2.0d);
        final double sinDlon = Math.sin(dLon / 2.0d);
        final double h = (sinDlat * sinDlat)
                + Math.cos(lat1) * Math.cos(lat2) * sinDlon * sinDlon;
        return 2.0d * earthRadiusKm * Math.asin(Math.sqrt(h));
    }

    private String formatOsrmCoordinate(final GeoPoint point) {
        return String.format(
                Locale.US,
                "%.6f,%.6f",
                point.lon(),
                point.lat()
        );
    }

    private static String buildGoogleMapsUrl(
            final GeoPoint origin,
            final List<GeoPoint> orderedPoints
    ) {
        if (orderedPoints.isEmpty()) {
            return "";
        }

        final String originParam = formatCoordinates(origin);
        final String destination = formatCoordinates(
                orderedPoints.get(orderedPoints.size() - 1)
        );
        final StringBuilder url = new StringBuilder(
                "https://www.google.com/maps/dir/?api=1&travelmode=driving"
        );
        url.append("&origin=").append(encode(originParam));
        url.append("&destination=").append(encode(destination));

        if (orderedPoints.size() > 1) {
            final List<String> waypoints = new ArrayList<>();
            for (int i = 0; i < orderedPoints.size() - 1; i++) {
                waypoints.add(formatCoordinates(orderedPoints.get(i)));
            }
            url.append("&waypoints=").append(encode(String.join("|", waypoints)));
        }
        return url.toString();
    }

    private String buildGoogleMapsAddressUrl(
            final RouteOriginInput origemInput,
            final List<DeliveryStopInput> stops
    ) {
        if (stops == null || stops.isEmpty()) {
            return "";
        }
        final String originParam = resolveMapsOriginParam(origemInput);
        final String destination = normalizeAddress(
                stops.get(stops.size() - 1).enderecoEntrega()
        );
        final StringBuilder url = new StringBuilder(
                "https://www.google.com/maps/dir/?api=1&travelmode=driving"
        );
        if (!originParam.isBlank()) {
            url.append("&origin=").append(encode(originParam));
        }
        if (!destination.isBlank()) {
            url.append("&destination=").append(encode(destination));
        }
        if (stops.size() > 1) {
            final List<String> waypoints = new ArrayList<>();
            for (int i = 0; i < stops.size() - 1; i++) {
                final String address = normalizeAddress(stops.get(i).enderecoEntrega());
                if (!address.isBlank()) {
                    waypoints.add(address);
                }
            }
            if (!waypoints.isEmpty()) {
                url.append("&waypoints=").append(encode(String.join("|", waypoints)));
            }
        }
        return url.toString();
    }

    private String resolveMapsOriginParam(final RouteOriginInput origemInput) {
        if (origemInput == null) {
            return "";
        }
        if (origemInput.hasCoordinates()) {
            return String.format(
                    Locale.US,
                    "%.6f,%.6f",
                    origemInput.latitude(),
                    origemInput.longitude()
            );
        }
        final String endereco = normalizeAddress(origemInput.endereco());
        if (!endereco.isBlank()) {
            return endereco;
        }
        return normalizeAddress(origemInput.displayLabel());
    }

    private static String formatCoordinates(final GeoPoint point) {
        return String.format(
                Locale.US,
                "%.6f,%.6f",
                point.lat(),
                point.lon()
        );
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String normalizeAddress(final String value) {
        return value == null ? "" : value.trim();
    }

    private List<String> geocodeCandidates(final String endereco) {
        final String normalized = normalizeAddress(endereco);
        if (normalized.isBlank()) {
            return List.of();
        }
        final Set<String> candidates = new LinkedHashSet<>();
        addGeocodeCandidateVariants(candidates, normalized);

        final String withoutComplements = normalizeAddress(
                stripCommonComplements(normalized)
        );
        addGeocodeCandidateVariants(candidates, withoutComplements);

        final String streetLevel = normalizeAddress(stripHouseNumber(
                withoutComplements.isBlank() ? normalized : withoutComplements
        ));
        addGeocodeCandidateVariants(candidates, streetLevel);
        return List.copyOf(candidates);
    }

    private String stripHouseNumber(final String endereco) {
        final String[] parts = endereco.split(",");
        final List<String> sanitizedParts = new ArrayList<>(parts.length);
        for (int index = 0; index < parts.length; index++) {
            final String part = parts[index].trim();
            if (part.isBlank()) {
                continue;
            }
            if (index > 0 && looksLikeStandaloneHouseNumber(part)) {
                continue;
            }
            sanitizedParts.add(part);
        }

        String candidate = String.join(", ", sanitizedParts);
        candidate = ADDRESS_NUMBER_MARKER.matcher(candidate).replaceAll(" ");
        candidate = TRAILING_HOUSE_NUMBER.matcher(candidate).replaceAll("");
        candidate = candidate.replaceAll("\\s{2,}", " ");
        candidate = candidate.replaceAll("\\s+,", ",");
        candidate = candidate.replaceAll(",\\s*,", ", ");
        return candidate.trim();
    }

    private String stripCommonComplements(final String endereco) {
        final String[] parts = endereco.split(",");
        final List<String> sanitizedParts = new ArrayList<>(parts.length);
        for (String rawPart : parts) {
            String part = normalizeAddress(rawPart);
            if (part.isBlank()) {
                continue;
            }
            part = normalizeAddress(
                    INLINE_COMPLEMENT_AFTER_NUMBER.matcher(part).replaceFirst("$1")
            );
            if (part.isBlank()
                    || LEADING_COMPLEMENT_SEGMENT.matcher(part).matches()) {
                continue;
            }
            sanitizedParts.add(part);
        }
        return String.join(", ", sanitizedParts);
    }

    private void addGeocodeCandidateVariants(
            final Set<String> candidates,
            final String endereco
    ) {
        final String normalized = normalizeAddress(endereco);
        if (normalized.isBlank()) {
            return;
        }
        addBaseGeocodeCandidateVariants(candidates, normalized);
        final String canonicalStreet = normalizeAddress(
                expandKnownStreetAliases(normalized)
        );
        if (!canonicalStreet.isBlank() && !canonicalStreet.equals(normalized)) {
            addBaseGeocodeCandidateVariants(candidates, canonicalStreet);
        }
    }

    private void addBaseGeocodeCandidateVariants(
            final Set<String> candidates,
            final String endereco
    ) {
        final String normalized = normalizeAddress(endereco);
        if (normalized.isBlank()) {
            return;
        }
        candidates.add(normalized);

        final String withoutNeighborhood = normalizeAddress(
                stripLikelyNeighborhood(normalized)
        );
        if (!withoutNeighborhood.isBlank()) {
            candidates.add(withoutNeighborhood);
        }

        final String withBrazil = normalizeAddress(appendBrazilHint(normalized));
        if (!withBrazil.isBlank()) {
            candidates.add(withBrazil);
        }

        final String withoutNeighborhoodWithBrazil = normalizeAddress(
                appendBrazilHint(withoutNeighborhood)
        );
        if (!withoutNeighborhoodWithBrazil.isBlank()) {
            candidates.add(withoutNeighborhoodWithBrazil);
        }
    }

    private String expandKnownStreetAliases(final String endereco) {
        final String normalized = normalizeAddress(endereco);
        if (normalized.isBlank()) {
            return normalized;
        }
        return normalized
                .replace(STORE_STREET_ABBREVIATED, STORE_STREET_CANONICAL)
                .replace(STORE_STREET_ABBREVIATED_NO_DOTS, STORE_STREET_CANONICAL);
    }

    private String stripLikelyNeighborhood(final String endereco) {
        final List<String> parts = splitAddressParts(endereco);
        if (parts.size() < 4) {
            return endereco;
        }
        final int neighborhoodIndex = resolveNeighborhoodIndex(parts);
        if (neighborhoodIndex < 0 || neighborhoodIndex >= parts.size()) {
            return endereco;
        }

        final List<String> simplified = new ArrayList<>(parts);
        simplified.remove(neighborhoodIndex);
        return String.join(", ", simplified);
    }

    private int resolveNeighborhoodIndex(final List<String> parts) {
        final int sizeWithoutCountry = BRAZIL_SEGMENT.matcher(parts.getLast()).find()
                ? parts.size() - 1
                : parts.size();
        final int sizeWithoutZip = sizeWithoutCountry > 0
                && isZipCodeSegment(parts.get(sizeWithoutCountry - 1))
                ? sizeWithoutCountry - 1
                : sizeWithoutCountry;
        if (sizeWithoutZip < 4) {
            return -1;
        }
        return sizeWithoutZip - 3;
    }

    private List<String> splitAddressParts(final String endereco) {
        final String[] rawParts = endereco.split(",");
        final List<String> parts = new ArrayList<>(rawParts.length);
        for (String rawPart : rawParts) {
            final String part = normalizeAddress(rawPart);
            if (!part.isBlank()) {
                parts.add(part);
            }
        }
        return parts;
    }

    private boolean isZipCodeSegment(final String value) {
        return value != null && CEP_SEGMENT.matcher(value.trim()).matches();
    }

    private String appendBrazilHint(final String endereco) {
        final String normalized = normalizeAddress(endereco);
        if (normalized.isBlank()
                || BRAZIL_SEGMENT.matcher(normalized).find()) {
            return normalized;
        }
        return normalized + ", Brasil";
    }

    private boolean looksLikeStandaloneHouseNumber(final String value) {
        final String normalized = value.trim();
        return normalized.matches("(?iu)^(?:n(?:u|ú)?m(?:ero)?|n[º°o]?|num|numero|nro|nr)?\\s*\\d+[\\p{L}\\d/-]*$");
    }

    private static String defaultString(final String value,
                                        final String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trimTrailingSlash(final String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }

    private static String normalize(final String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal toScale(final double value) {
        return BigDecimal.valueOf(value).setScale(
                DISTANCE_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private record GeoPoint(double lat, double lon) {
    }

    private record ResolvedOrigin(
            String displayLabel,
            GeoPoint point
    ) {
    }

    private record ResolvedStop(
            DeliveryStopInput input,
            GeoPoint point
    ) {
    }

    private record MatrixData(
            double[][] travelCosts,
            double[][] distanceKm,
            long[][] durationSeconds
    ) {
    }

    public record DeliveryStopInput(
            Long pedidoId,
            String clienteNome,
            String enderecoEntrega,
            String codigoEntrega,
            String status
    ) {
    }

    public record DeliveryStopPlan(
            int ordem,
            Long pedidoId,
            String clienteNome,
            String enderecoEntrega,
            String codigoEntrega,
            String status,
            BigDecimal distanciaAnteriorKm,
            BigDecimal distanciaAcumuladaKm,
            Double latitude,
            Double longitude,
            Long duracaoAnteriorSegundos,
            Long duracaoAcumuladaSegundos
    ) {
    }

    public record LegDistanceEstimate(
            String referenciaAnterior,
            BigDecimal distanciaKm
    ) {
    }

    public record PlannedRoute(
            String origem,
            BigDecimal custoTotal,
            BigDecimal distanciaTotalKm,
            List<DeliveryStopPlan> paradas,
            String mapaUrl
    ) {
    }

    public record RouteOriginInput(
            String displayLabel,
            String endereco,
            Double latitude,
            Double longitude
    ) {
        public boolean hasCoordinates() {
            return latitude != null
                    && longitude != null
                    && Double.isFinite(latitude)
                    && Double.isFinite(longitude);
        }
    }

    private static final class GeocodeRateLimitException
            extends IllegalStateException {

        private GeocodeRateLimitException(final String message) {
            super(message);
        }
    }
}
