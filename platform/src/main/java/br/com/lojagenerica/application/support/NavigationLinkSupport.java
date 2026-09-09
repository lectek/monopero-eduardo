package br.com.lojagenerica.application.support;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class NavigationLinkSupport {

    private NavigationLinkSupport() {
    }

    public static String googleMapsDirections(final String destination) {
        if (isBlank(destination)) {
            return "";
        }
        return "https://www.google.com/maps/dir/?api=1&travelmode=driving"
                + "&dir_action=navigate&destination="
                + encode(destination.trim());
    }

    public static String googleMapsDirections(
            final BigDecimal latitude,
            final BigDecimal longitude,
            final String fallbackAddress
    ) {
        return googleMapsDirections(resolveDestination(
                latitude,
                longitude,
                fallbackAddress
        ));
    }

    public static String googleMapsDirections(
            final BigDecimal originLatitude,
            final BigDecimal originLongitude,
            final BigDecimal destinationLatitude,
            final BigDecimal destinationLongitude,
            final String fallbackDestination
    ) {
        final String destination = resolveDestination(
                destinationLatitude,
                destinationLongitude,
                fallbackDestination
        );
        if (isBlank(destination)) {
            return "";
        }
        final StringBuilder url = new StringBuilder(
                "https://www.google.com/maps/dir/?api=1&travelmode=driving"
        );
        url.append("&dir_action=navigate&destination=")
                .append(encode(destination));
        if (originLatitude != null && originLongitude != null) {
            url.append("&origin=")
                    .append(originLatitude.toPlainString())
                    .append(",")
                    .append(originLongitude.toPlainString());
        }
        return url.toString();
    }

    public static String googleMapsEmbed(
            final BigDecimal latitude,
            final BigDecimal longitude,
            final String fallbackQuery
    ) {
        final String query = resolveDestination(latitude, longitude, fallbackQuery);
        if (isBlank(query)) {
            return "";
        }
        return "https://www.google.com/maps?q="
                + encode(query)
                + "&z=16&output=embed";
    }

    public static String wazeNavigate(final String destination) {
        if (isBlank(destination)) {
            return "";
        }
        return "https://www.waze.com/ul?q=" + encode(destination.trim())
                + "&navigate=yes";
    }

    public static String wazeNavigate(
            final BigDecimal latitude,
            final BigDecimal longitude,
            final String fallbackAddress
    ) {
        if (latitude != null && longitude != null) {
            return "https://www.waze.com/ul?ll="
                    + latitude.toPlainString()
                    + ","
                    + longitude.toPlainString()
                    + "&navigate=yes";
        }
        return wazeNavigate(fallbackAddress);
    }

    private static String resolveDestination(
            final BigDecimal latitude,
            final BigDecimal longitude,
            final String fallbackAddress
    ) {
        if (latitude != null && longitude != null) {
            return latitude.toPlainString() + "," + longitude.toPlainString();
        }
        return fallbackAddress;
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
