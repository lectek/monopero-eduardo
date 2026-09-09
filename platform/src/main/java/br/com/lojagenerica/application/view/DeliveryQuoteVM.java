package br.com.lojagenerica.application.view;

import java.math.BigDecimal;

public record DeliveryQuoteVM(
        boolean available,
        String referenceAddress,
        BigDecimal distanceKm,
        BigDecimal freeRadiusKm,
        BigDecimal billableDistanceKm,
        BigDecimal ratePerKm,
        BigDecimal standardShippingAmount,
        BigDecimal prioritySurcharge,
        BigDecimal priorityShippingAmount,
        String summary,
        String detail
) {

    public static DeliveryQuoteVM available(
            final String referenceAddress,
            final BigDecimal distanceKm,
            final BigDecimal freeRadiusKm,
            final BigDecimal billableDistanceKm,
            final BigDecimal ratePerKm,
            final BigDecimal standardShippingAmount,
            final BigDecimal prioritySurcharge,
            final BigDecimal priorityShippingAmount,
            final String summary,
            final String detail
    ) {
        return new DeliveryQuoteVM(
                true,
                blankToDefault(referenceAddress),
                distanceKm,
                freeRadiusKm,
                billableDistanceKm,
                ratePerKm,
                standardShippingAmount,
                prioritySurcharge,
                priorityShippingAmount,
                blankToDefault(summary),
                blankToDefault(detail)
        );
    }

    public static DeliveryQuoteVM unavailable(
            final BigDecimal freeRadiusKm,
            final BigDecimal ratePerKm,
            final BigDecimal prioritySurcharge,
            final String summary,
            final String detail
    ) {
        return new DeliveryQuoteVM(
                false,
                "",
                null,
                freeRadiusKm,
                BigDecimal.ZERO,
                ratePerKm,
                BigDecimal.ZERO,
                prioritySurcharge,
                prioritySurcharge,
                blankToDefault(summary),
                blankToDefault(detail)
        );
    }

    private static String blankToDefault(final String value) {
        return value == null ? "" : value.trim();
    }
}
