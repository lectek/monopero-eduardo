package br.com.lojagenerica.application.service;

import br.com.lojagenerica.application.core.settings.AppSettingService;
import br.com.lojagenerica.application.view.PaymentMethodVM;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Portado do ParaisoPet (application/service/PaymentMethodService). Única
 * adaptação: removido o suporte a InfinityPay (gateway não usado neste
 * projeto — decisão do usuário de ficar só com Mercado Pago) — o método
 * {@code isInfinitePayGatewaySelected} some e {@code isOnlineGatewaySelected}
 * passa a depender só do Mercado Pago.
 */
@Service
public class PaymentMethodService {

    private static final String KEY_GATEWAY = "pg.gateway";
    private static final String GATEWAY_MERCADO_PAGO = "mercadopago";
    private static final Set<String> CHECKOUT_SUPPORTED_VALUES = Set.of("pix", "credito", "debito");

    private final AppSettingService settings;
    private final ObjectMapper objectMapper;

    public PaymentMethodService(AppSettingService settings, ObjectMapper objectMapper) {
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    public List<PaymentMethodVM> listActiveMethods() {
        List<PaymentMethodVM> methods = new ArrayList<>();
        final boolean onlineGateway = isMercadoPagoGatewaySelected();
        final String onlineType = onlineGateway ? "online" : "offline";

        boolean pixAtivo = settings.getBoolean("pg.pix_ativo", true);
        boolean cartaoAtivo = settings.getBoolean("pg.cartao_ativo", true);
        boolean boletoAtivo = settings.getBoolean("pg.boleto_ativo", false);
        boolean dinheiroAtivo = settings.getBoolean("pg.dinheiro_ativo", true);

        if (pixAtivo) {
            methods.add(new PaymentMethodVM("pix", onlineGateway ? "PIX (recomendado)" : "PIX manual", onlineType));
        }
        if (boletoAtivo) {
            methods.add(new PaymentMethodVM("boleto", onlineGateway ? "Boleto Bancario" : "Boleto manual", onlineType));
        }
        if (cartaoAtivo) {
            methods.add(new PaymentMethodVM("credito", onlineGateway ? "Cartao de Credito" : "Cartao de Credito manual", onlineType));
            methods.add(new PaymentMethodVM("debito", onlineGateway ? "Cartao de Debito" : "Cartao de Debito manual", onlineType));
        }
        if (dinheiroAtivo) {
            methods.add(new PaymentMethodVM("dinheiro", "Dinheiro", "offline"));
        }

        methods.addAll(loadCustomPaymentMethods());
        return methods;
    }

    public boolean isActiveValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (PaymentMethodVM method : listActiveMethods()) {
            if (value.equals(method.value())) {
                return true;
            }
        }
        return false;
    }

    public List<PaymentMethodVM> listCheckoutMethods() {
        return listActiveMethods().stream()
                .filter(method -> CHECKOUT_SUPPORTED_VALUES.contains(method.value()))
                .map(this::toCheckoutMethod)
                .toList();
    }

    public boolean isCheckoutSupportedValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return listCheckoutMethods().stream().anyMatch(method -> value.equals(method.value()));
    }

    public String resolveLabel(String value) {
        if (value == null) return "";
        for (PaymentMethodVM method : listActiveMethods()) {
            if (value.equals(method.value())) {
                return method.label();
            }
        }
        return value;
    }

    public String resolveCheckoutLabel(String value) {
        if (value == null) {
            return "";
        }
        for (PaymentMethodVM method : listCheckoutMethods()) {
            if (value.equals(method.value())) {
                return method.label();
            }
        }
        return resolveLabel(value);
    }

    public String resolveType(final String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        for (PaymentMethodVM method : listActiveMethods()) {
            if (value.equals(method.value())) {
                return method.tipo() == null ? "" : method.tipo().trim().toLowerCase(Locale.ROOT);
            }
        }
        return inferTypeFromValue(value);
    }

    public boolean isOfflineValue(final String value) {
        final String type = resolveType(value);
        return "offline".equals(type) || "custom".equals(type);
    }

    private List<PaymentMethodVM> loadCustomPaymentMethods() {
        String raw = settings.getOrDefault("pg.custom_methods", "[]");
        List<PaymentMethodVM> out = new ArrayList<>();
        try {
            List<CustomPaymentMethod> list = objectMapper.readValue(raw, new TypeReference<List<CustomPaymentMethod>>() {});
            if (list == null) {
                return out;
            }
            for (CustomPaymentMethod m : list) {
                if (m == null || !m.isAtivo()) {
                    continue;
                }
                String tipo = normalizeType(m.getTipo());
                if ("pos".equals(tipo)) {
                    continue;
                }
                String label = m.getNome();
                if (m.getTaxa() != null && m.getTaxa().compareTo(BigDecimal.ZERO) > 0) {
                    label = label + " (" + m.getTaxa().toPlainString() + "%)";
                }
                String value = "custom:" + (m.getId() == null ? m.getNome().toLowerCase() : m.getId());
                out.add(new PaymentMethodVM(value, label, tipo));
            }
        } catch (Exception ex) {
            return out;
        }
        return out;
    }

    private String normalizeType(String raw) {
        String tipo = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return tipo.isBlank() ? "custom" : tipo;
    }

    private String inferTypeFromValue(final String value) {
        final String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!isOnlineGatewaySelected()) {
            return switch (normalized) {
                case "pix", "credito", "debito", "boleto" -> "offline";
                default -> inferBuiltInType(normalized);
            };
        }
        return inferBuiltInType(normalized);
    }

    private String inferBuiltInType(final String normalized) {
        return switch (normalized) {
            case "dinheiro" -> "offline";
            case "pix", "credito", "debito", "boleto" -> "online";
            default -> normalized.startsWith("custom:") ? "custom" : "";
        };
    }

    private boolean isMercadoPagoGatewaySelected() {
        final String raw = firstNonBlank(
                System.getenv("PAYMENT_GATEWAY"),
                System.getenv("PG_GATEWAY"),
                settings.getOrDefault(KEY_GATEWAY, GATEWAY_MERCADO_PAGO)
        );
        final String normalized = raw == null
                ? ""
                : raw.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        return GATEWAY_MERCADO_PAGO.equals(normalized);
    }

    private boolean isOnlineGatewaySelected() {
        return isMercadoPagoGatewaySelected();
    }

    private String firstNonBlank(final String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private PaymentMethodVM toCheckoutMethod(PaymentMethodVM method) {
        return switch (method.value()) {
            case "pix" -> new PaymentMethodVM("pix", "PIX", method.tipo());
            case "credito" -> new PaymentMethodVM("credito", "Cartao de Credito", method.tipo());
            case "debito" -> new PaymentMethodVM("debito", "Cartao de Debito", method.tipo());
            default -> method;
        };
    }

    private static class CustomPaymentMethod {
        private String id;
        private String nome;
        private String tipo;
        private BigDecimal taxa;
        private boolean ativo;

        public String getId() { return id; }
        @SuppressWarnings("unused") public void setId(String id) { this.id = id; }
        public String getNome() { return nome; }
        @SuppressWarnings("unused") public void setNome(String nome) { this.nome = nome; }
        public String getTipo() { return tipo; }
        @SuppressWarnings("unused") public void setTipo(String tipo) { this.tipo = tipo; }
        public BigDecimal getTaxa() { return taxa; }
        @SuppressWarnings("unused") public void setTaxa(BigDecimal taxa) { this.taxa = taxa; }
        public boolean isAtivo() { return ativo; }
        @SuppressWarnings("unused") public void setAtivo(boolean ativo) { this.ativo = ativo; }
    }
}
