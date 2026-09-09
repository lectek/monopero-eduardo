package br.com.lojagenerica.multitenancy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

/**
 * Schema-por-tenant num único pool Postgres: cada aquisição de conexão seta
 * {@code search_path} pro schema pedido, cada devolução reseta pra
 * {@code pg_catalog}. Os dois lados são incondicionais de propósito — o
 * risco #1 do projeto inteiro é uma conexão devolvida ao pool com o
 * search_path de um tenant ainda setado respondendo silenciosamente à
 * próxima query de outro tenant (ver docs/CONTEXTO.md e
 * MultiTenancyIsolationIT).
 */
@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    /** DDL não aceita bind parameter — o nome do schema é validado antes de entrar num SET. */
    private static final Pattern VALID_SCHEMA = Pattern.compile("^[a-z0-9_]{3,63}$");

    private final DataSource dataSource;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        setSearchPath(connection, "pg_catalog");
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        resetAndClose(connection);
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        setSearchPath(connection, validate(tenantIdentifier));
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        resetAndClose(connection);
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }

    private static void resetAndClose(Connection connection) throws SQLException {
        try {
            setSearchPath(connection, "pg_catalog");
        } finally {
            connection.close();
        }
    }

    private static void setSearchPath(Connection connection, String schema) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO \"" + schema + "\"");
        }
    }

    private static String validate(String schema) {
        if (schema == null || !VALID_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("Nome de schema de tenant inválido: " + schema);
        }
        return schema;
    }
}
