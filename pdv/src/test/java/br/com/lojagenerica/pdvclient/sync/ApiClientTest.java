package br.com.lojagenerica.pdvclient.sync;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lojagenerica.pdvclient.local.EventoOutbox;
import br.com.lojagenerica.pdvclient.shared.TipoEventoPdv;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Um {@link HttpServer} embutido no lugar do servidor real — o contrato
 * exercitado aqui (nomes de campo, formato do envelope) é o mesmo provado
 * do lado servidor em {@code PdvSyncFlowIT} (ver platform/); este teste só
 * garante que ApiClient fala exatamente esse dialeto.
 */
class ApiClientTest {

    private HttpServer server;
    private ApiClient client;
    private final AtomicReference<String> ultimaChaveRecebida = new AtomicReference<>();

    @BeforeEach
    void iniciarServidorFalso() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/pdv/sync/push", exchange -> {
            ultimaChaveRecebida.set(exchange.getRequestHeaders().getFirst("X-Terminal-Api-Key"));
            JSONObject corpo = new JSONObject(new JSONTokener(exchange.getRequestBody()));
            JSONObject evento = corpo.getJSONArray("eventos").getJSONObject(0);
            String uuid = evento.getString("uuid");

            JSONObject resultado = new JSONObject();
            resultado.put("uuid", uuid);
            resultado.put("status", "ACEITO");
            resultado.put("servidorId", 123);
            resultado.put("erro", JSONObject.NULL);
            JSONObject resposta = new JSONObject();
            resposta.put("resultados", new org.json.JSONArray(List.of(resultado)));
            responder(exchange, 200, resposta.toString());
        });
        server.createContext("/api/v1/pdv/sync/pull", exchange -> {
            JSONObject produto = new JSONObject();
            produto.put("id", 10);
            produto.put("nome", "Parafuso");
            produto.put("codigoInterno", "COD1");
            produto.put("precoVenda", 12.5);
            produto.put("unidadeEstoqueId", 2);
            produto.put("controlaEstoque", true);
            produto.put("status", "ATIVO");
            produto.put("atualizadoEm", Instant.parse("2026-01-01T00:00:00Z").toString());

            JSONObject resposta = new JSONObject();
            resposta.put("itens", new org.json.JSONArray(List.of(produto)));
            resposta.put("proximoCursor", "2026-01-01T00:00:00Z");
            resposta.put("temMais", false);
            responder(exchange, 200, resposta.toString());
        });
        server.start();
        client = new ApiClient("http://localhost:" + server.getAddress().getPort(), "empresa_001.segredo");
    }

    @AfterEach
    void pararServidor() {
        server.stop(0);
    }

    private void responder(HttpExchange exchange, int status, String corpo) throws java.io.IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    void pushEnviaChaveDeApiEEnvelopaOsEventosCorretamente() throws Exception {
        UUID uuid = UUID.randomUUID();
        EventoOutbox evento = new EventoOutbox(1L, uuid, TipoEventoPdv.VENDA_REGISTRADA, Instant.now(),
                "{\"localEstoqueId\":1}", 0);

        List<ResultadoEvento> resultados = client.push(List.of(evento));

        assertThat(ultimaChaveRecebida.get()).isEqualTo("empresa_001.segredo");
        assertThat(resultados).hasSize(1);
        assertThat(resultados.get(0).uuid()).isEqualTo(uuid);
        assertThat(resultados.get(0).status()).isEqualTo("ACEITO");
        assertThat(resultados.get(0).servidorId()).isEqualTo(123L);
        assertThat(resultados.get(0).aceitoOuDuplicado()).isTrue();
    }

    @Test
    void pullDeserializaProdutosECursor() throws Exception {
        PullResultado resultado = client.pull("produto", null, 500);

        assertThat(resultado.itens()).hasSize(1);
        ProdutoRemoto produto = resultado.itens().get(0);
        assertThat(produto.id()).isEqualTo(10L);
        assertThat(produto.nome()).isEqualTo("Parafuso");
        assertThat(produto.precoVenda()).isEqualTo(12.5);
        assertThat(resultado.proximoCursor()).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(resultado.temMais()).isFalse();
    }
}
