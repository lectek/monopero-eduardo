package br.com.lojagenerica.pdv;

import br.com.lojagenerica.multitenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autenticação de terminal é separada da de usuário (JWT) — o caixa
 * sincroniza o backlog da madrugada sem ninguém logado. A chave de API é
 * um segredo de alta entropia, não senha humana: hash SHA-256 simples
 * (rápido, indexável) é o correto aqui, não bcrypt (que é pra senha de
 * baixa entropia, deliberadamente lento).
 *
 * <p>A chave embute o schema do tenant como prefixo
 * ({@code "<schema>.<segredo>"}) — é assim que
 * {@code TerminalAuthenticationFilter} sabe qual schema resolver antes de
 * poder consultar a tabela {@code terminal} (que é por-tenant).
 */
@Service
public class TerminalService {

    private final TerminalRepository terminalRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public TerminalService(TerminalRepository terminalRepository) {
        this.terminalRepository = terminalRepository;
    }

    public record TerminalCriadoResultado(Long terminalId, String apiKey) {
    }

    /** {@code apiKey} só existe em texto puro nesta resposta — o servidor guarda só o hash. */
    @Transactional
    public TerminalCriadoResultado criar(String nome) {
        String schema = TenantContext.require();
        String segredo = gerarSegredo();
        String apiKey = schema + "." + segredo;
        Terminal terminal = terminalRepository.save(new Terminal(nome, sha256Hex(apiKey)));
        return new TerminalCriadoResultado(terminal.getId(), apiKey);
    }

    public static String resolverSchemaDaChave(String apiKey) {
        int ponto = apiKey == null ? -1 : apiKey.indexOf('.');
        return ponto <= 0 ? null : apiKey.substring(0, ponto);
    }

    public static String sha256Hex(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }

    private String gerarSegredo() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
