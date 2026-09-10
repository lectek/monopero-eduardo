package br.com.lojagenerica.pdvclient.sync;

import br.com.lojagenerica.pdvclient.local.EventoOutbox;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Fala com {@code /api/v1/pdv/sync/**} do servidor, autenticado pela chave
 * de API do terminal (não JWT de usuário — ver
 * {@code TerminalAuthenticationFilter} no servidor). Cada evento do outbox
 * já guarda seu payload como texto JSON pronto ({@code sync_outbox.payload_json}):
 * este cliente só precisa reidratá-lo pra dentro do envelope
 * {@code EventoPushRequest}, nunca precisa conhecer os campos de dentro.
 */
public final class ApiClient {

    private static final String HEADER_API_KEY = "X-Terminal-Api-Key";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    public ApiClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    public List<ResultadoEvento> push(List<EventoOutbox> eventos) throws IOException, InterruptedException {
        JSONArray eventosJson = new JSONArray();
        for (EventoOutbox evento : eventos) {
            JSONObject eventoJson = new JSONObject();
            eventoJson.put("uuid", evento.eventoUuid().toString());
            eventoJson.put("tipo", evento.tipo().name());
            eventoJson.put("ocorridoEm", evento.ocorridoEm().toString());
            eventoJson.put("payload", new JSONObject(new JSONTokener(evento.payloadJson())));
            eventosJson.put(eventoJson);
        }
        JSONObject corpo = new JSONObject();
        corpo.put("eventos", eventosJson);

        JSONObject resposta = enviar("POST", "/api/v1/pdv/sync/push", corpo.toString());
        JSONArray resultadosJson = resposta.getJSONArray("resultados");
        List<ResultadoEvento> resultados = new ArrayList<>();
        for (int i = 0; i < resultadosJson.length(); i++) {
            JSONObject r = resultadosJson.getJSONObject(i);
            resultados.add(new ResultadoEvento(
                    UUID.fromString(r.getString("uuid")),
                    r.getString("status"),
                    r.isNull("servidorId") ? null : r.getLong("servidorId"),
                    r.isNull("erro") ? null : r.getString("erro")));
        }
        return resultados;
    }

    public PullResultado pull(String recurso, String desde, int limite) throws IOException, InterruptedException {
        StringBuilder path = new StringBuilder("/api/v1/pdv/sync/pull?recurso=").append(recurso).append("&limite=").append(limite);
        if (desde != null && !desde.isBlank()) {
            path.append("&desde=").append(java.net.URLEncoder.encode(desde, java.nio.charset.StandardCharsets.UTF_8));
        }
        JSONObject resposta = enviar("GET", path.toString(), null);
        JSONArray itensJson = resposta.getJSONArray("itens");
        List<ProdutoRemoto> produtos = new ArrayList<>();
        for (int i = 0; i < itensJson.length(); i++) {
            JSONObject p = itensJson.getJSONObject(i);
            produtos.add(new ProdutoRemoto(
                    p.getLong("id"),
                    p.getString("nome"),
                    p.optString("codigoInterno", null),
                    p.isNull("precoVenda") ? null : p.getDouble("precoVenda"),
                    p.isNull("unidadeEstoqueId") ? null : p.getLong("unidadeEstoqueId"),
                    p.getBoolean("controlaEstoque"),
                    p.optString("status", null),
                    Instant.parse(p.getString("atualizadoEm"))));
        }
        return new PullResultado(produtos, resposta.optString("proximoCursor", null), resposta.getBoolean("temMais"));
    }

    /** Cadastro pequeno, sem cursor — servidor sempre devolve a lista inteira. */
    public List<FormaPagamentoRemoto> pullFormasPagamento() throws IOException, InterruptedException {
        JSONObject resposta = enviar("GET", "/api/v1/pdv/sync/pull?recurso=forma_pagamento&limite=500", null);
        JSONArray itensJson = resposta.getJSONArray("itens");
        List<FormaPagamentoRemoto> formas = new ArrayList<>();
        for (int i = 0; i < itensJson.length(); i++) {
            JSONObject f = itensJson.getJSONObject(i);
            formas.add(new FormaPagamentoRemoto(f.getLong("id"), f.getString("nome"), f.getString("natureza"),
                    f.getBoolean("afetaCaixa"), f.getBoolean("ativo")));
        }
        return formas;
    }

    /** Cadastro pequeno, sem cursor — servidor sempre devolve a lista inteira. */
    public List<LocalEstoqueRemoto> pullLocaisEstoque() throws IOException, InterruptedException {
        JSONObject resposta = enviar("GET", "/api/v1/pdv/sync/pull?recurso=local_estoque&limite=500", null);
        JSONArray itensJson = resposta.getJSONArray("itens");
        List<LocalEstoqueRemoto> locais = new ArrayList<>();
        for (int i = 0; i < itensJson.length(); i++) {
            JSONObject l = itensJson.getJSONObject(i);
            locais.add(new LocalEstoqueRemoto(l.getLong("id"), l.getString("nome"), l.optString("tipo", null),
                    l.getBoolean("principal"), l.getBoolean("ativo")));
        }
        return locais;
    }

    /** Usado pela tela de pareamento pra validar a chave antes de salvar — um pull vazio já confirma autenticação + tenant certos. */
    public boolean testarConexao() {
        try {
            pull("produto", null, 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JSONObject enviar(String metodo, String path, String corpo) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header(HEADER_API_KEY, apiKey)
                .header("Content-Type", "application/json");
        HttpRequest request = switch (metodo) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
            case "GET" -> builder.GET().build();
            default -> throw new IllegalArgumentException("Método não suportado: " + metodo);
        };
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Servidor respondeu HTTP " + response.statusCode() + ": " + response.body());
        }
        return new JSONObject(new JSONTokener(response.body()));
    }
}
