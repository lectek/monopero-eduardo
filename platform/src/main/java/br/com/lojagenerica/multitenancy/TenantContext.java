package br.com.lojagenerica.multitenancy;

/**
 * Schema do tenant atual desta thread. Setado por {@link TenantResolutionFilter}
 * (ou pelos runners de migration/provisionamento), lido por
 * {@link SchemaTenantIdentifierResolver} e {@link SchemaMultiTenantConnectionProvider}.
 *
 * <p>Sempre limpo em {@code finally} por quem seta — nunca deixar vazar entre
 * requests. {@link TenantGuard} falha fechado se algum repositório rodar sem
 * contexto setado.
 */
public final class TenantContext {

    /** Schema do control plane — usado quando nenhum tenant foi resolvido ainda. */
    public static final String PLATAFORMA = "plataforma";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String schema) {
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("schema de tenant não pode ser vazio");
        }
        CURRENT.set(schema);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static boolean isSet() {
        return CURRENT.get() != null;
    }

    public static String require() {
        String schema = CURRENT.get();
        if (schema == null) {
            throw new IllegalStateException(
                    "TenantContext não setado — toda operação de repositório precisa de um "
                            + "tenant resolvido antes (ver TenantResolutionFilter/TenantIterator)");
        }
        return schema;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
