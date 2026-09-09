package br.com.minimercadinho.saas.domain.financeiro.mercadopago;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MercadoPagoCheckoutHttpClient implements MercadoPagoCheckoutClient {

    private static final String PREFERENCES_ENDPOINT =
            "https://api.mercadopago.com/checkout/preferences";
    private static final String CREATE_PAYMENT_ENDPOINT =
            "https://api.mercadopago.com/v1/payments";
    private static final String PAYMENTS_ENDPOINT =
            "https://api.mercadopago.com/v1/payments/{paymentId}";

    @Override
    public PreferenceResponse createPreference(
            final String accessToken,
            final PreferenceRequest request
    ) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("external_reference", request.externalReference());
        putIfNotBlank(body, "notification_url", request.notificationUrl());
        body.put("back_urls", buildBackUrls(request));
        body.put("auto_return", "approved");
        body.put("items", buildItems(request.items()));
        body.put("payer", buildPayer(request.payer()));

        try {
            @SuppressWarnings("unchecked")
            final Map<String, Object> response = RestClient.create()
                    .post()
                    .uri(PREFERENCES_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return toPreferenceResponse(response);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "Mercado Pago recusou a criacao da preferencia: HTTP "
                            + ex.getStatusCode().value(),
                    ex
            );
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Falha ao criar a preferencia de pagamento no Mercado Pago.",
                    ex
            );
        }
    }

    @Override
    public PaymentResponse createPixPayment(
            final String accessToken,
            final PixPaymentRequest request
    ) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("transaction_amount", request.transactionAmount());
        putIfNotBlank(body, "description", request.description());
        body.put("payment_method_id", "pix");
        putIfNotBlank(body, "external_reference", request.externalReference());
        putIfNotBlank(body, "notification_url", request.notificationUrl());
        body.put("payer", buildPayer(request.payer()));

        try {
            @SuppressWarnings("unchecked")
            final Map<String, Object> response = RestClient.create()
                    .post()
                    .uri(CREATE_PAYMENT_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        putHeaderIfNotBlank(
                                headers,
                                "X-Idempotency-Key",
                                request.idempotencyKey()
                        );
                    })
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return toPaymentResponse(response);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "Mercado Pago recusou a criacao do pagamento Pix: HTTP "
                            + ex.getStatusCode().value(),
                    ex
            );
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Falha ao criar o pagamento Pix no Mercado Pago.",
                    ex
            );
        }
    }

    @Override
    public PaymentResponse fetchPayment(
            final String accessToken,
            final String paymentId
    ) {
        try {
            @SuppressWarnings("unchecked")
            final Map<String, Object> response = RestClient.create()
                    .get()
                    .uri(PAYMENTS_ENDPOINT, paymentId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(Map.class);
            return toPaymentResponse(response);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "Mercado Pago recusou a consulta do pagamento: HTTP "
                            + ex.getStatusCode().value(),
                    ex
            );
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Falha ao consultar o pagamento no Mercado Pago.",
                    ex
            );
        }
    }

    private Map<String, Object> buildBackUrls(final PreferenceRequest request) {
        final Map<String, Object> backUrls = new LinkedHashMap<>();
        putIfNotBlank(backUrls, "success", request.successUrl());
        putIfNotBlank(backUrls, "pending", request.pendingUrl());
        putIfNotBlank(backUrls, "failure", request.failureUrl());
        return backUrls;
    }

    private List<Map<String, Object>> buildItems(
            final List<PreferenceItem> items
    ) {
        final List<Map<String, Object>> payload = new ArrayList<>();
        if (items == null) {
            return payload;
        }
        for (PreferenceItem item : items) {
            if (item == null) {
                continue;
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            putIfNotBlank(entry, "id", item.id());
            putIfNotBlank(entry, "title", item.title());
            entry.put("quantity", item.quantity());
            entry.put("unit_price", item.unitPrice());
            putIfNotBlank(entry, "currency_id", item.currencyId());
            payload.add(entry);
        }
        return payload;
    }

    private Map<String, Object> buildPayer(final PreferencePayer payer) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        if (payer == null) {
            return payload;
        }
        putIfNotBlank(payload, "name", payer.name());
        putIfNotBlank(payload, "email", payer.email());
        if (text(payer.cpf()).isBlank()) {
            return payload;
        }
        final Map<String, Object> identification = new LinkedHashMap<>();
        identification.put("type", "CPF");
        identification.put("number", payer.cpf());
        payload.put("identification", identification);
        return payload;
    }

    private PreferenceResponse toPreferenceResponse(
            final Map<String, Object> response
    ) {
        if (response == null || response.isEmpty()) {
            throw new IllegalStateException(
                    "Mercado Pago retornou uma preferencia vazia."
            );
        }
        final String preferenceId = text(response.get("id"));
        final String initPoint = text(response.get("init_point"));
        final String sandboxInitPoint = text(response.get("sandbox_init_point"));
        if (preferenceId.isBlank()
                || (initPoint.isBlank() && sandboxInitPoint.isBlank())) {
            throw new IllegalStateException(
                    "Mercado Pago nao retornou os dados essenciais da preferencia."
            );
        }
        return new PreferenceResponse(
                preferenceId,
                initPoint,
                sandboxInitPoint,
                response
        );
    }

    private PaymentResponse toPaymentResponse(final Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            throw new IllegalStateException(
                    "Mercado Pago retornou um pagamento vazio."
            );
        }
        final String paymentId = text(response.get("id"));
        if (paymentId.isBlank()) {
            throw new IllegalStateException(
                    "Mercado Pago nao retornou o identificador do pagamento."
            );
        }
        return new PaymentResponse(
                paymentId,
                text(response.get("external_reference")),
                text(response.get("status")),
                text(response.get("status_detail")),
                text(response.get("payment_method_id")),
                text(response.get("payment_type_id")),
                parseOffsetDateTime(response.get("date_approved")),
                parseOffsetDateTime(response.get("date_last_updated")),
                resolveTicketUrl(response),
                resolveQrCode(response),
                resolveQrCodeBase64(response),
                response
        );
    }

    private String resolveTicketUrl(final Map<String, Object> response) {
        return firstNonBlank(
                nestedText(response, "point_of_interaction", "transaction_data", "ticket_url"),
                nestedText(response, "transaction_details", "ticket_url")
        );
    }

    private String resolveQrCode(final Map<String, Object> response) {
        return nestedText(
                response,
                "point_of_interaction",
                "transaction_data",
                "qr_code"
        );
    }

    private String resolveQrCodeBase64(final Map<String, Object> response) {
        return nestedText(
                response,
                "point_of_interaction",
                "transaction_data",
                "qr_code_base64"
        );
    }

    @SuppressWarnings("unchecked")
    private String nestedText(
            final Map<String, Object> source,
            final String firstKey,
            final String secondKey,
            final String thirdKey
    ) {
        if (source == null) {
            return "";
        }
        final Object first = source.get(firstKey);
        if (!(first instanceof Map<?, ?> firstMap)) {
            return "";
        }
        final Object second = ((Map<String, Object>) firstMap).get(secondKey);
        if (!(second instanceof Map<?, ?> secondMap)) {
            return "";
        }
        return text(((Map<String, Object>) secondMap).get(thirdKey));
    }

    @SuppressWarnings("unchecked")
    private String nestedText(
            final Map<String, Object> source,
            final String firstKey,
            final String secondKey
    ) {
        if (source == null) {
            return "";
        }
        final Object first = source.get(firstKey);
        if (!(first instanceof Map<?, ?> firstMap)) {
            return "";
        }
        return text(((Map<String, Object>) firstMap).get(secondKey));
    }

    private OffsetDateTime parseOffsetDateTime(final Object value) {
        final String textValue = text(value);
        if (textValue.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(textValue);
        } catch (Exception ex) {
            return null;
        }
    }

    private void putIfNotBlank(
            final Map<String, Object> target,
            final String key,
            final String value
    ) {
        if (!text(value).isBlank()) {
            target.put(key, value.trim());
        }
    }

    private void putHeaderIfNotBlank(
            final org.springframework.http.HttpHeaders headers,
            final String key,
            final String value
    ) {
        if (!text(value).isBlank()) {
            headers.add(key, value.trim());
        }
    }

    private String firstNonBlank(final String first, final String second) {
        return text(first).isBlank() ? text(second) : first.trim();
    }

    private String text(final Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
