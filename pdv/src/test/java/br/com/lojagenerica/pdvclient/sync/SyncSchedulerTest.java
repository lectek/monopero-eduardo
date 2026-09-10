package br.com.lojagenerica.pdvclient.sync;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.lojagenerica.pdvclient.local.ItemVendaLocal;
import br.com.lojagenerica.pdvclient.local.LocalDb;
import br.com.lojagenerica.pdvclient.local.OutboxDao;
import br.com.lojagenerica.pdvclient.local.PagamentoVendaLocal;
import br.com.lojagenerica.pdvclient.local.ProdutoCacheDao;
import br.com.lojagenerica.pdvclient.local.SyncCursorDao;
import br.com.lojagenerica.pdvclient.local.VendaLocalDao;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Ponta a ponta do lado cliente: uma venda enfileirada localmente é
 * drenada pelo scheduler e some do outbox pendente — sem precisar do
 * servidor real (o contrato de rede já está provado em
 * {@code ApiClientTest} e, do lado servidor, em {@code PdvSyncFlowIT}).
 */
class SyncSchedulerTest {

    @TempDir
    Path tempDir;

    @Test
    void vendaEnfileiradaEDrenadaPeloSchedulerSaiDosPendentes() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/pdv/sync/push", exchange -> {
            JSONObject corpo = new JSONObject(new JSONTokener(exchange.getRequestBody()));
            JSONArray eventos = corpo.getJSONArray("eventos");
            JSONArray resultados = new JSONArray();
            for (int i = 0; i < eventos.length(); i++) {
                JSONObject resultado = new JSONObject();
                resultado.put("uuid", eventos.getJSONObject(i).getString("uuid"));
                resultado.put("status", "ACEITO");
                resultado.put("servidorId", 1000 + i);
                resultado.put("erro", JSONObject.NULL);
                resultados.put(resultado);
            }
            JSONObject resposta = new JSONObject();
            resposta.put("resultados", resultados);
            responder(exchange, resposta.toString());
        });
        server.createContext("/api/v1/pdv/sync/pull", exchange -> {
            JSONObject resposta = new JSONObject();
            resposta.put("itens", new JSONArray());
            resposta.put("proximoCursor", JSONObject.NULL);
            resposta.put("temMais", false);
            responder(exchange, resposta.toString());
        });
        server.start();
        try (LocalDb db = new LocalDb(tempDir.resolve("pdv-local.db"))) {
            OutboxDao outboxDao = new OutboxDao(db);
            VendaLocalDao vendaLocalDao = new VendaLocalDao(db, outboxDao);
            ProdutoCacheDao produtoCacheDao = new ProdutoCacheDao(db);
            SyncCursorDao syncCursorDao = new SyncCursorDao(db);

            vendaLocalDao.registrarVenda(1L, null, null, null,
                    List.of(new ItemVendaLocal(10L, "Parafuso", 1.0, 2L, 10.0, null)),
                    List.of(new PagamentoVendaLocal(5L, 10.0, 10.0, 0.0)));
            assertThat(outboxDao.contarPendentes()).isEqualTo(1);

            ApiClient apiClient = new ApiClient("http://localhost:" + server.getAddress().getPort(), "empresa_001.segredo");
            SyncScheduler scheduler = new SyncScheduler(apiClient, outboxDao, produtoCacheDao, syncCursorDao);
            scheduler.iniciar();
            try {
                aguardarAte(() -> outboxDao.contarPendentes() == 0, 3000);
            } finally {
                scheduler.parar();
            }

            assertThat(outboxDao.contarPendentes()).isZero();
        } finally {
            server.stop(0);
        }
    }

    private interface Condicao {
        boolean satisfeita() throws Exception;
    }

    private void aguardarAte(Condicao condicao, long timeoutMs) throws Exception {
        long limite = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < limite) {
            if (condicao.satisfeita()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condição não satisfeita após " + timeoutMs + "ms");
    }

    private void responder(HttpExchange exchange, String corpo) throws java.io.IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
