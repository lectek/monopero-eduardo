package br.com.minimercadinho.saas.domain.financeiro.mercadopago;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface MercadoPagoCheckoutClient {

    PreferenceResponse createPreference(
            String accessToken,
            PreferenceRequest request
    );

    PaymentResponse createPixPayment(
            String accessToken,
            PixPaymentRequest request
    );

    PaymentResponse fetchPayment(
            String accessToken,
            String paymentId
    );

    record PreferenceRequest(
            String externalReference,
            String notificationUrl,
            String successUrl,
            String pendingUrl,
            String failureUrl,
            List<PreferenceItem> items,
            PreferencePayer payer
    ) {
    }

    record PreferenceItem(
            String id,
            String title,
            int quantity,
            BigDecimal unitPrice,
            String currencyId
    ) {
    }

    record PreferencePayer(
            String name,
            String email,
            String cpf
    ) {
    }

    record PixPaymentRequest(
            String externalReference,
            String notificationUrl,
            String description,
            BigDecimal transactionAmount,
            PreferencePayer payer,
            String idempotencyKey
    ) {
    }

    record PreferenceResponse(
            String preferenceId,
            String initPoint,
            String sandboxInitPoint,
            Map<String, Object> rawPayload
    ) {
    }

    record PaymentResponse(
            String paymentId,
            String externalReference,
            String status,
            String statusDetail,
            String paymentMethodId,
            String paymentTypeId,
            OffsetDateTime approvedAt,
            OffsetDateTime updatedAt,
            String ticketUrl,
            String qrCode,
            String qrCodeBase64,
            Map<String, Object> rawPayload
    ) {
    }
}
