package br.com.lojagenerica.application.view;

public record DeliveryEstimateVM(
        boolean available,
        String summary,
        String detail
) {

    public static DeliveryEstimateVM available(
            final String summary,
            final String detail
    ) {
        return new DeliveryEstimateVM(true, blankToDefault(summary), blankToDefault(detail));
    }

    public static DeliveryEstimateVM unavailable(
            final String summary,
            final String detail
    ) {
        return new DeliveryEstimateVM(false, blankToDefault(summary), blankToDefault(detail));
    }

    private static String blankToDefault(final String value) {
        return value == null ? "" : value.trim();
    }
}
