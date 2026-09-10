package br.com.lojagenerica.domain.financeiro.mercadopago;

import br.com.lojagenerica.application.core.settings.AppSettingService;
import br.com.lojagenerica.core.parceiro.Cliente;
import br.com.lojagenerica.core.venda.ItemVenda;
import br.com.lojagenerica.core.venda.TipoPagamentoOnline;
import br.com.lojagenerica.core.venda.Venda;
import br.com.lojagenerica.core.venda.VendaPagamentoGateway;
import br.com.lojagenerica.core.venda.VendaPagamentoGatewayRepository;
import br.com.lojagenerica.core.venda.VendaService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Reescrito na Fase C pra operar sobre {@link Venda}+{@link VendaPagamentoGateway}
 * em vez de {@code PedidoEntity} — a lógica de negócio (criar Pix/preferência,
 * checar elegibilidade, aplicar status de pagamento) é a mesma herdada do
 * ParaisoPet/versão anterior deste arquivo; só o agregado mudou. Único token
 * estático (mesma ideia do {@code MP_ACCESS_TOKEN} que o IMS já usa) — este
 * SaaS ainda é de 1 tenant por processo, sem resolução de token por vendedor.
 */
@Service
public class MercadoPagoCheckoutService {

    public static final String PROVIDER_NAME = "mercadopago";
    public static final String KEY_ACCESS_TOKEN = "pg.mp.access_token";
    public static final String KEY_WEBHOOK_URL = "pg.webhook_url";
    private static final String PAYMENT_STATUS_APPROVED = "approved";
    private static final String PIX_PAYMENT_METHOD = "pix";
    private static final String PIX_PAYMENT_TYPE = "bank_transfer";

    private final AppSettingService settings;
    private final VendaPagamentoGatewayRepository gatewayRepository;
    private final VendaService vendaService;
    private final MercadoPagoCheckoutClient checkoutClient;
    private final String appBaseUrl;

    public MercadoPagoCheckoutService(
            final AppSettingService settingsValue,
            final VendaPagamentoGatewayRepository gatewayRepositoryValue,
            final VendaService vendaServiceValue,
            final MercadoPagoCheckoutClient checkoutClientValue,
            @Value("${app.web.base-url:http://localhost:8080}") final String appBaseUrlValue
    ) {
        this.settings = settingsValue;
        this.gatewayRepository = gatewayRepositoryValue;
        this.vendaService = vendaServiceValue;
        this.checkoutClient = checkoutClientValue;
        this.appBaseUrl = appBaseUrlValue;
    }

    @Transactional
    public void assertReadyForOnlineCheckout() {
        if (resolveAccessToken().isBlank()) {
            throw new IllegalStateException(
                    "Configure o access token do Mercado Pago (MP_ACCESS_TOKEN / " + KEY_ACCESS_TOKEN + ")."
            );
        }
    }

    @Transactional
    public Optional<CheckoutPreferenceResult> ensureCheckoutForVenda(final Venda venda, final VendaPagamentoGateway gateway) {
        if (!isPendingPaymentStatus(venda)) {
            return Optional.empty();
        }
        if (isPixOrder(gateway)) {
            if (hasPixPresentation(gateway)) {
                return Optional.of(buildCheckoutResultFromGateway(gateway));
            }
            if (!text(gateway.getPaymentId()).isBlank()) {
                try {
                    syncPayment(venda, gateway, gateway.getPaymentId());
                } catch (IllegalStateException ignored) {
                    // Mantém o pagamento atual e deixa a página renderizar.
                }
                return Optional.of(buildCheckoutResultFromGateway(gateway));
            }
        } else if (!text(gateway.getCheckoutUrl()).isBlank()) {
            return Optional.of(buildCheckoutResultFromGateway(gateway));
        }
        return Optional.of(createCheckout(venda, gateway, CheckoutRequest.fromCliente(venda.getCliente())));
    }

    @Transactional
    public CheckoutPreferenceResult createCheckout(final Venda venda, final VendaPagamentoGateway gateway,
                                                    final CheckoutRequest checkoutRequest) {
        if (!isPendingPaymentStatus(venda)) {
            throw new IllegalArgumentException("A venda nao esta aguardando pagamento online.");
        }

        final String accessToken = requireAccessToken();
        final String externalReference = resolveExternalReference(venda, gateway);
        final String detailUrl = buildOrderDetailUrl(venda.getId());
        final String notificationUrl = resolveNotificationUrl();

        gateway.setProvider(PROVIDER_NAME);
        gateway.setExternalReference(externalReference);
        gatewayRepository.save(gateway);

        if (gateway.getTipoPagamentoOnline() == TipoPagamentoOnline.PIX) {
            final MercadoPagoCheckoutClient.PaymentResponse payment = checkoutClient.createPixPayment(
                    accessToken,
                    new MercadoPagoCheckoutClient.PixPaymentRequest(
                            externalReference,
                            notificationUrl,
                            buildPixDescription(venda),
                            safeTransactionAmount(venda),
                            buildPayer(checkoutRequest, venda),
                            buildPixIdempotencyKey(externalReference)
                    )
            );
            applyPaymentToGateway(venda, gateway, payment);
            gateway.setPreferenceId(null);
            gatewayRepository.save(gateway);
            return buildCheckoutResultFromGateway(gateway);
        }

        final MercadoPagoCheckoutClient.PreferenceResponse response = checkoutClient.createPreference(
                accessToken,
                new MercadoPagoCheckoutClient.PreferenceRequest(
                        externalReference,
                        notificationUrl,
                        detailUrl,
                        detailUrl,
                        detailUrl,
                        buildPreferenceItems(venda),
                        new MercadoPagoCheckoutClient.PreferencePayer(
                                checkoutRequest.payerName(),
                                checkoutRequest.payerEmail(),
                                normalizeCpf(checkoutRequest.payerCpf())
                        )
                )
        );

        gateway.setPreferenceId(response.preferenceId());
        gateway.setCheckoutUrl(firstNonBlank(response.initPoint(), response.sandboxInitPoint()));
        gateway.setPaymentTicketUrl(null);
        gateway.setPixQrCode(null);
        gateway.setPixQrCodeBase64(null);
        gatewayRepository.save(gateway);

        return buildCheckoutResultFromGateway(gateway);
    }

    @Transactional
    public PaymentSyncResult syncPayment(final Venda venda, final VendaPagamentoGateway gateway, final String paymentId) {
        if (venda == null || text(paymentId).isBlank()) {
            return PaymentSyncResult.ignored();
        }
        final MercadoPagoCheckoutClient.PaymentResponse payment = checkoutClient.fetchPayment(requireAccessToken(), paymentId);
        applyPaymentToGateway(venda, gateway, payment);
        gatewayRepository.save(gateway);
        return PaymentSyncResult.updated(venda.getId(), payment.paymentId(), payment.status());
    }

    /** Webhook do Mercado Pago: como só existe um token/loja, busca o pagamento direto, sem resolver "vendedor". */
    @Transactional
    public PaymentSyncResult handleWebhookNotification(final String type, final String topic, final String paymentId) {
        if (!isPaymentNotification(type, topic) || text(paymentId).isBlank()) {
            return PaymentSyncResult.ignored();
        }
        final MercadoPagoCheckoutClient.PaymentResponse payment = checkoutClient.fetchPayment(requireAccessToken(), paymentId);
        final VendaPagamentoGateway gateway = resolveGatewayForPayment(payment);
        if (gateway == null) {
            return PaymentSyncResult.ignored();
        }
        final Venda venda = gateway.getVenda();
        applyPaymentToGateway(venda, gateway, payment);
        gatewayRepository.save(gateway);
        return PaymentSyncResult.updated(venda.getId(), payment.paymentId(), payment.status());
    }

    public boolean hasNotificationUrlConfigured() {
        return !resolveNotificationUrl().isBlank();
    }

    private String resolveAccessToken() {
        return firstNonBlank(
                System.getenv("MP_ACCESS_TOKEN"),
                settings.getOrDefault(KEY_ACCESS_TOKEN, "")
        );
    }

    private String requireAccessToken() {
        final String token = resolveAccessToken();
        if (token.isBlank()) {
            throw new IllegalStateException(
                    "Access token do Mercado Pago nao configurado (env MP_ACCESS_TOKEN ou setting " + KEY_ACCESS_TOKEN + ")."
            );
        }
        return token;
    }

    private VendaPagamentoGateway resolveGatewayForPayment(final MercadoPagoCheckoutClient.PaymentResponse payment) {
        final String paymentId = text(payment.paymentId());
        if (!paymentId.isBlank()) {
            final Optional<VendaPagamentoGateway> byPaymentId = gatewayRepository.findByPaymentId(paymentId);
            if (byPaymentId.isPresent()) {
                return byPaymentId.get();
            }
        }
        final String externalReference = text(payment.externalReference());
        if (!externalReference.isBlank()) {
            return gatewayRepository.findByExternalReference(externalReference).orElse(null);
        }
        return null;
    }

    /**
     * Efeito colateral importante: pagamento aprovado dispara
     * {@link VendaService#confirmarPagamento} (grava o ledger de saída),
     * recusado/cancelado dispara {@link VendaService#cancelar} — a venda
     * ainda em RASCUNHO cancela sem tocar estoque (nunca foi confirmada).
     */
    private void applyPaymentToGateway(final Venda venda, final VendaPagamentoGateway gateway,
                                        final MercadoPagoCheckoutClient.PaymentResponse payment) {
        gateway.setProvider(PROVIDER_NAME);
        gateway.setPaymentId(payment.paymentId());
        gateway.setExternalReference(firstNonBlank(payment.externalReference(), gateway.getExternalReference()));
        gateway.setPaymentStatus(payment.status());
        gateway.setPaymentStatusDetail(payment.statusDetail());
        gateway.setPaymentUpdatedAt(toInstant(payment.updatedAt()));
        gateway.setPaymentTicketUrl(payment.ticketUrl());
        gateway.setPixQrCode(payment.qrCode());
        gateway.setPixQrCodeBase64(payment.qrCodeBase64());
        if (gateway.getTipoPagamentoOnline() == TipoPagamentoOnline.PIX) {
            gateway.setCheckoutUrl(firstNonBlank(payment.ticketUrl(), gateway.getCheckoutUrl()));
        }
        gateway.setFormaPagamentoRecebida(buildReceivedPaymentLabel(payment));
        gateway.setPagamentoDivergente(isPaymentMethodDivergent(gateway, payment));

        final String status = text(payment.status()).toLowerCase(Locale.ROOT);
        if (PAYMENT_STATUS_APPROVED.equals(status)) {
            if (gateway.getPagamentoRecebidoEm() == null) {
                gateway.setPagamentoRecebidoEm(firstNonNull(toInstant(payment.approvedAt()), Instant.now()));
            }
            vendaService.confirmarPagamento(venda.getId());
            return;
        }
        if ("rejected".equals(status) || "cancelled".equals(status)) {
            vendaService.cancelar(venda.getId(), "Pagamento " + status + " no Mercado Pago", null);
        }
        // pending/in_process: venda continua em RASCUNHO, nada a mudar nela.
    }

    private List<MercadoPagoCheckoutClient.PreferenceItem> buildPreferenceItems(final Venda venda) {
        final List<MercadoPagoCheckoutClient.PreferenceItem> items = new ArrayList<>();
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (ItemVenda item : venda.getItens()) {
            final String title = text(item.getDescricaoSnapshot());
            final BigDecimal unitPrice = item.getPrecoUnitario() == null ? BigDecimal.ZERO : item.getPrecoUnitario();
            final int quantity = Math.max(item.getQuantidade().intValue(), 1);
            items.add(new MercadoPagoCheckoutClient.PreferenceItem(
                    firstNonBlank(item.getId() == null ? "" : String.valueOf(item.getId()), "item-" + (items.size() + 1)),
                    title.isBlank() ? "Item da venda" : title,
                    quantity,
                    unitPrice,
                    "BRL"
            ));
            itemsTotal = itemsTotal.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        }

        final BigDecimal orderTotal = venda.getTotal() == null ? BigDecimal.ZERO : venda.getTotal();
        final BigDecimal difference = orderTotal.subtract(itemsTotal);
        if (difference.compareTo(BigDecimal.ZERO) > 0) {
            items.add(new MercadoPagoCheckoutClient.PreferenceItem("venda-frete", "Frete", 1, difference, "BRL"));
        }
        if (items.isEmpty()) {
            items.add(new MercadoPagoCheckoutClient.PreferenceItem("venda-" + venda.getId(), "Venda #" + venda.getId(), 1, orderTotal, "BRL"));
        }
        return items;
    }

    private String resolveExternalReference(final Venda venda, final VendaPagamentoGateway gateway) {
        final String current = text(gateway.getExternalReference());
        if (!current.isBlank()) {
            return current;
        }
        return "venda:" + venda.getId();
    }

    private String resolveNotificationUrl() {
        final String configuredWebhook = text(settings.getOrDefault(KEY_WEBHOOK_URL, ""));
        if (!configuredWebhook.isBlank()) {
            return configuredWebhook;
        }
        final String normalizedBaseUrl = normalizeBaseUrl(appBaseUrl);
        if (!normalizedBaseUrl.startsWith("https://")) {
            return "";
        }
        return normalizedBaseUrl + "/webhooks/mercadopago";
    }

    private String buildOrderDetailUrl(final Long vendaId) {
        return UriComponentsBuilder.fromUriString(normalizeBaseUrl(appBaseUrl))
                .path("/cliente/pedidos/{id}")
                .buildAndExpand(vendaId)
                .toUriString();
    }

    private boolean isPaymentNotification(final String type, final String topic) {
        final String normalizedType = text(type).toLowerCase(Locale.ROOT);
        final String normalizedTopic = text(topic).toLowerCase(Locale.ROOT);
        return "payment".equals(normalizedType) || "payment".equals(normalizedTopic)
                || normalizedType.startsWith("payment.") || normalizedTopic.startsWith("payment.");
    }

    private boolean isPaymentMethodDivergent(final VendaPagamentoGateway gateway, final MercadoPagoCheckoutClient.PaymentResponse payment) {
        final String paymentType = text(payment.paymentTypeId()).toLowerCase(Locale.ROOT);
        final String paymentMethod = text(payment.paymentMethodId()).toLowerCase(Locale.ROOT);
        if (paymentType.isBlank() && paymentMethod.isBlank()) {
            return false;
        }
        return switch (gateway.getTipoPagamentoOnline()) {
            case PIX -> !PIX_PAYMENT_METHOD.equals(paymentMethod) && !PIX_PAYMENT_TYPE.equals(paymentType);
            case BOLETO -> !"ticket".equals(paymentType);
            case CARTAO_CREDITO -> !"credit_card".equals(paymentType);
            case CARTAO_DEBITO -> !"debit_card".equals(paymentType);
        };
    }

    private String buildReceivedPaymentLabel(final MercadoPagoCheckoutClient.PaymentResponse payment) {
        final String paymentType = text(payment.paymentTypeId());
        final String paymentMethod = text(payment.paymentMethodId());
        if (paymentType.isBlank()) {
            return truncate(paymentMethod, 30);
        }
        if (paymentMethod.isBlank()) {
            return truncate(paymentType, 30);
        }
        return truncate(paymentType + ":" + paymentMethod, 30);
    }

    private String normalizeBaseUrl(final String value) {
        final String normalized = text(value);
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private boolean isPendingPaymentStatus(final Venda venda) {
        return venda != null && venda.getStatus() == br.com.lojagenerica.core.venda.StatusVenda.RASCUNHO;
    }

    private MercadoPagoCheckoutClient.PreferencePayer buildPayer(final CheckoutRequest checkoutRequest, final Venda venda) {
        final String payerEmail = firstNonBlank(
                checkoutRequest == null ? "" : checkoutRequest.payerEmail(),
                venda != null && venda.getCliente() != null ? venda.getCliente().getEmail() : ""
        );
        if (text(payerEmail).isBlank()) {
            throw new IllegalStateException("Informe um e-mail valido para gerar o Pix do Mercado Pago.");
        }
        return new MercadoPagoCheckoutClient.PreferencePayer(
                firstNonBlank(
                        checkoutRequest == null ? "" : checkoutRequest.payerName(),
                        venda != null && venda.getCliente() != null ? venda.getCliente().getNome() : ""
                ),
                payerEmail,
                normalizeCpf(firstNonBlank(
                        checkoutRequest == null ? "" : checkoutRequest.payerCpf(),
                        venda != null && venda.getCliente() != null ? venda.getCliente().getDocumento() : ""
                ))
        );
    }

    private boolean isPixOrder(final VendaPagamentoGateway gateway) {
        return gateway != null && gateway.getTipoPagamentoOnline() == TipoPagamentoOnline.PIX;
    }

    private boolean hasPixPresentation(final VendaPagamentoGateway gateway) {
        return !text(gateway == null ? null : gateway.getPixQrCode()).isBlank()
                || !text(gateway == null ? null : gateway.getPixQrCodeBase64()).isBlank()
                || !text(gateway == null ? null : gateway.getPaymentTicketUrl()).isBlank();
    }

    private CheckoutPreferenceResult buildCheckoutResultFromGateway(final VendaPagamentoGateway gateway) {
        return new CheckoutPreferenceResult(
                gateway.getCheckoutUrl(),
                gateway.getPreferenceId(),
                gateway.getPaymentTicketUrl(),
                gateway.getPixQrCode(),
                gateway.getPixQrCodeBase64()
        );
    }

    private String buildPixDescription(final Venda venda) {
        return "Venda #" + venda.getId();
    }

    private BigDecimal safeTransactionAmount(final Venda venda) {
        final BigDecimal amount = venda == null ? null : venda.getTotal();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("A venda precisa ter um valor valido para gerar o Pix.");
        }
        return amount;
    }

    private String buildPixIdempotencyKey(final String externalReference) {
        final String seed = text(externalReference) + ":pix";
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String normalizeCpf(final String value) {
        return text(value).replaceAll("\\D", "");
    }

    private String truncate(final String value, final int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Instant toInstant(final OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private Instant firstNonNull(final Instant first, final Instant second) {
        return first != null ? first : second;
    }

    private String firstNonBlank(final String first, final String second) {
        return text(first).isBlank() ? text(second) : first.trim();
    }

    private String text(final Object value) {
        return value == null ? "" : value.toString().trim();
    }

    public record CheckoutRequest(String payerName, String payerEmail, String payerCpf) {
        public static CheckoutRequest fromCliente(final Cliente cliente) {
            if (cliente == null) {
                return empty();
            }
            return new CheckoutRequest(cliente.getNome(), cliente.getEmail(), cliente.getDocumento());
        }

        public static CheckoutRequest empty() {
            return new CheckoutRequest("", "", "");
        }
    }

    public record CheckoutPreferenceResult(
            String checkoutUrl,
            String preferenceId,
            String pixTicketUrl,
            String pixQrCode,
            String pixQrCodeBase64
    ) {
    }

    public record PaymentSyncResult(Long vendaId, String paymentId, String paymentStatus, boolean updated) {
        public static PaymentSyncResult updated(final Long vendaId, final String paymentId, final String paymentStatus) {
            return new PaymentSyncResult(vendaId, paymentId, paymentStatus, true);
        }

        public static PaymentSyncResult ignored() {
            return new PaymentSyncResult(null, "", "", false);
        }
    }
}
