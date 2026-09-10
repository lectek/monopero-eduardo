package br.com.lojagenerica.pdvclient.config;

import java.time.Instant;

/**
 * Estado de pareamento do terminal. {@code terminalApiKey} embute o schema
 * do tenant como prefixo ({@code "<schema>.<segredo>"}, ver
 * {@code TerminalService} no servidor) — o cliente nunca precisa saber o
 * nome do schema separadamente, só repassa a chave inteira no header
 * {@code X-Terminal-Api-Key}.
 */
public record ConfiguracaoLocal(
        String servidorBaseUrl,
        String terminalApiKey,
        String terminalNome,
        String empresaNome,
        Instant pareadoEm,
        String mpAccessToken,
        String mpPayerEmail) {

    public boolean pareado() {
        return servidorBaseUrl != null && !servidorBaseUrl.isBlank()
                && terminalApiKey != null && !terminalApiKey.isBlank();
    }
}
