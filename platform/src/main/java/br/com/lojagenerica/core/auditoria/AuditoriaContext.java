package br.com.lojagenerica.core.auditoria;

/**
 * Quem/de-onde da request atual, pra {@link AuditoriaService} anexar em todo
 * evento sem cada chamador precisar passar isso explicitamente. Populado
 * por {@code JwtAuthenticationFilter}, sempre limpo em {@code finally}.
 */
public final class AuditoriaContext {

    private static final ThreadLocal<Dados> CURRENT = new ThreadLocal<>();

    private AuditoriaContext() {
    }

    public record Dados(Long usuarioId, String usuarioEmail, String ip, String userAgent) {
    }

    public static void set(Long usuarioId, String usuarioEmail, String ip, String userAgent) {
        CURRENT.set(new Dados(usuarioId, usuarioEmail, ip, userAgent));
    }

    public static Dados get() {
        Dados dados = CURRENT.get();
        return dados != null ? dados : new Dados(null, null, null, null);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
