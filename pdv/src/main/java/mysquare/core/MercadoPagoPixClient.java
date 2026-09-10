package mysquare.core;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Talks to the Mercado Pago Payments API (v1/payments) to create and poll a Pix charge. */
public class MercadoPagoPixClient {

    /** Result of creating a Pix charge: the id to poll, plus what to show the customer. */
    public static class PixCharge {
        public final String paymentId;
        public final String qrCodeBase64;
        public final String qrCodeCopiaCola;

        PixCharge(String paymentId, String qrCodeBase64, String qrCodeCopiaCola) {
            this.paymentId = paymentId;
            this.qrCodeBase64 = qrCodeBase64;
            this.qrCodeCopiaCola = qrCodeCopiaCola;
        }
    }

    private static final String API_BASE = "https://api.mercadopago.com";

    private final String accessToken;
    private final String payerEmail;

    public MercadoPagoPixClient(String accessToken, String payerEmail) {
        this.accessToken = accessToken;
        this.payerEmail = payerEmail;
    }

    public PixCharge criarCobranca(double valor, String descricao) throws IOException {
        JSONObject payer = new JSONObject();
        payer.put("email", payerEmail);

        JSONObject body = new JSONObject();
        body.put("transaction_amount", valor);
        body.put("description", descricao);
        body.put("payment_method_id", "pix");
        body.put("payer", payer);

        JSONObject response = post("/v1/payments", body);

        String paymentId = String.valueOf(response.get("id"));
        JSONObject transactionData = response.getJSONObject("point_of_interaction").getJSONObject("transaction_data");
        String qrCodeBase64 = transactionData.optString("qr_code_base64", null);
        String qrCode = transactionData.optString("qr_code", null);
        return new PixCharge(paymentId, qrCodeBase64, qrCode);
    }

    /** Mercado Pago status string for a payment: "pending", "approved", "cancelled", "rejected", etc. */
    public String consultarStatus(String paymentId) throws IOException {
        return get("/v1/payments/" + paymentId).getString("status");
    }

    private JSONObject post(String path, JSONObject body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(API_BASE + path).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Idempotency-Key", UUID.randomUUID().toString());
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        return readResponse(conn);
    }

    private JSONObject get(String path) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(API_BASE + path).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        return readResponse(conn);
    }

    private JSONObject readResponse(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        JSONObject json = new JSONObject(readAll(stream));
        if (status < 200 || status >= 300) {
            throw new IOException(json.optString("message", "Erro Mercado Pago (HTTP " + status + ")"));
        }
        return json;
    }

    private String readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString("UTF-8");
    }
}
