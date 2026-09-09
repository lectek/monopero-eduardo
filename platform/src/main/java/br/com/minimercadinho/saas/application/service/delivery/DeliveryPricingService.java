package br.com.minimercadinho.saas.application.service.delivery;

import br.com.minimercadinho.saas.adapters.outbound.persistence.entity.CustomerEntity;
import br.com.minimercadinho.saas.adapters.outbound.persistence.repository.CustomerRepository;
import br.com.minimercadinho.saas.application.config.AppProps;
import br.com.minimercadinho.saas.application.core.settings.AppSettingService;
import br.com.minimercadinho.saas.application.view.DeliveryQuoteVM;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portado do ParaisoPet (application/service/delivery/DeliveryPricingService).
 * Única adaptação real: {@code Authentication} (Spring Security, não usado
 * neste projeto) virou {@code customerEmail} — uma String simples com o
 * e-mail do cliente logado, resolvida por quem chama (o filtro/serviço de
 * auth do cliente, quando existir).
 */
@Service
public class DeliveryPricingService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryPricingService.class);

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    /**
     * Não existe raio grátis nesta loja (diferente do ParaisoPet original): até
     * DEFAULT_MIN_FARE_RADIUS_KM cobra a tarifa mínima cheia; depois disso, soma
     * o valor por km excedente em cima da mínima. Decisão do dono.
     */
    private static final BigDecimal DEFAULT_MIN_FARE_RADIUS_KM = new BigDecimal("3.00");
    private static final BigDecimal DEFAULT_MIN_FARE_VALUE = new BigDecimal("5.00");
    private static final BigDecimal DEFAULT_RATE_PER_KM = new BigDecimal("2.00");
    private static final BigDecimal DEFAULT_PRIORITY_SURCHARGE = new BigDecimal("15.00");
    private static final String SETTING_MIN_FARE_RADIUS_KM = "entrega.frete.raio_minimo_km";
    private static final String SETTING_MIN_FARE_VALUE = "entrega.frete.valor_minimo";
    private static final String SETTING_RATE_PER_KM = "entrega.frete.valor_km_excedente";
    private static final String SETTING_PRIORITY_SURCHARGE = "entrega.frete.prioritario.acrescimo";
    private static final String SHIPPING_UNAVAILABLE_SUMMARY = "Nao foi possivel calcular o frete para esse endereco.";
    private static final String SHIPPING_UNAVAILABLE_DETAIL = "Revise a rua e tente novamente.";

    private final DeliveryRouteService deliveryRouteService;
    private final AppSettingService appSettingService;
    private final AppProps appProps;
    private final CustomerRepository customerRepository;

    public DeliveryPricingService(
            final DeliveryRouteService deliveryRouteServiceValue,
            final AppSettingService appSettingServiceValue,
            final AppProps appPropsValue,
            final CustomerRepository customerRepositoryValue
    ) {
        this.deliveryRouteService = deliveryRouteServiceValue;
        this.appSettingService = appSettingServiceValue;
        this.appProps = appPropsValue;
        this.customerRepository = customerRepositoryValue;
    }

    @Transactional(readOnly = true)
    public DeliveryQuoteVM quoteForCheckout(final String typedAddress, final String customerEmail) {
        final String resolvedAddress = resolveAddress(typedAddress, customerEmail);
        return quoteForAddress(resolvedAddress);
    }

    @Transactional(readOnly = true)
    public DeliveryQuoteVM quoteForAddress(final String rawAddress) {
        final BigDecimal minFareRadiusKm = readPositiveDecimal(SETTING_MIN_FARE_RADIUS_KM, DEFAULT_MIN_FARE_RADIUS_KM);
        final BigDecimal minFareValue = readNonNegativeDecimal(SETTING_MIN_FARE_VALUE, DEFAULT_MIN_FARE_VALUE);
        final BigDecimal ratePerKm = readNonNegativeDecimal(SETTING_RATE_PER_KM, DEFAULT_RATE_PER_KM);
        final BigDecimal prioritySurcharge = readNonNegativeDecimal(SETTING_PRIORITY_SURCHARGE, DEFAULT_PRIORITY_SURCHARGE);

        final String address = normalize(rawAddress);
        if (address.isBlank()) {
            return DeliveryQuoteVM.unavailable(
                    minFareRadiusKm, ratePerKm, prioritySurcharge,
                    "Informe o endereco para calcular o frete",
                    buildPolicyDetail(minFareRadiusKm, minFareValue, ratePerKm)
            );
        }

        final String origin = normalize(appProps.getAddressQuery());
        if (origin.isBlank()) {
            return DeliveryQuoteVM.unavailable(
                    minFareRadiusKm, ratePerKm, prioritySurcharge,
                    "Nao foi possivel preparar a cotacao de entrega",
                    "A origem da loja nao esta configurada para calcular a rota."
            );
        }

        try {
            final BigDecimal distanceKm = deliveryRouteService.estimateDistanceBetween(origin, address);
            if (distanceKm == null) {
                return DeliveryQuoteVM.unavailable(
                        minFareRadiusKm, ratePerKm, prioritySurcharge,
                        SHIPPING_UNAVAILABLE_SUMMARY, SHIPPING_UNAVAILABLE_DETAIL
                );
            }

            // Sem raio grátis: até minFareRadiusKm cobra a tarifa mínima cheia;
            // depois disso, soma o valor por km excedente em cima da mínima.
            final BigDecimal billableDistanceKm = max(distanceKm.subtract(minFareRadiusKm), BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            final BigDecimal standardShippingAmount = minFareValue.add(billableDistanceKm.multiply(ratePerKm))
                    .setScale(2, RoundingMode.HALF_UP);
            final BigDecimal priorityShippingAmount = standardShippingAmount.add(prioritySurcharge)
                    .setScale(2, RoundingMode.HALF_UP);

            return DeliveryQuoteVM.available(
                    address, distanceKm, minFareRadiusKm, billableDistanceKm, ratePerKm,
                    standardShippingAmount, prioritySurcharge, priorityShippingAmount,
                    buildSummary(standardShippingAmount),
                    buildAvailableDetail(distanceKm, minFareRadiusKm, minFareValue, ratePerKm)
            );
        } catch (RuntimeException ex) {
            log.warn("Nao foi possivel calcular o frete para '{}': {}", address, ex.getMessage());
            return DeliveryQuoteVM.unavailable(
                    minFareRadiusKm, ratePerKm, prioritySurcharge,
                    SHIPPING_UNAVAILABLE_SUMMARY, SHIPPING_UNAVAILABLE_DETAIL
            );
        }
    }

    public BigDecimal resolveShippingAmount(final String address, final boolean priority) {
        final DeliveryQuoteVM quote = quoteForAddress(address);
        if (!quote.available()) {
            throw new IllegalArgumentException(SHIPPING_UNAVAILABLE_SUMMARY);
        }
        return priority ? quote.priorityShippingAmount() : quote.standardShippingAmount();
    }

    private String resolveAddress(final String typedAddress, final String customerEmail) {
        final String explicitAddress = normalize(typedAddress);
        if (!explicitAddress.isBlank()) {
            return explicitAddress;
        }
        final String email = normalize(customerEmail);
        if (email.isBlank()) {
            return "";
        }
        return customerRepository.findByEmailOrCpf(email)
                .map(CustomerEntity::getEndereco)
                .map(this::normalize)
                .orElse("");
    }

    private String buildSummary(final BigDecimal shippingAmount) {
        return "Frete padrao em " + formatCurrency(shippingAmount);
    }

    private String buildAvailableDetail(
            final BigDecimal distanceKm, final BigDecimal minFareRadiusKm,
            final BigDecimal minFareValue, final BigDecimal ratePerKm
    ) {
        return "Distancia estimada de " + formatDistance(distanceKm) + ". "
                + buildPolicyDetail(minFareRadiusKm, minFareValue, ratePerKm);
    }

    private String buildPolicyDetail(
            final BigDecimal minFareRadiusKm, final BigDecimal minFareValue, final BigDecimal ratePerKm
    ) {
        return "Ate " + formatDistance(minFareRadiusKm) + " cobramos a tarifa minima de "
                + formatCurrency(minFareValue) + "; depois somamos "
                + formatCurrency(ratePerKm) + " por km excedente.";
    }

    private BigDecimal readPositiveDecimal(final String key, final BigDecimal defaultValue) {
        final BigDecimal value = appSettingService.getDecimal(key, defaultValue);
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return defaultValue;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal readNonNegativeDecimal(final String key, final BigDecimal defaultValue) {
        final BigDecimal value = appSettingService.getDecimal(key, defaultValue);
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return defaultValue;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal max(final BigDecimal left, final BigDecimal right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    private String formatCurrency(final BigDecimal value) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(value);
    }

    private String formatDistance(final BigDecimal value) {
        if (value == null) {
            return "0 km";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + " km";
    }

    private String normalize(final String value) {
        return value == null ? "" : value.trim();
    }
}
