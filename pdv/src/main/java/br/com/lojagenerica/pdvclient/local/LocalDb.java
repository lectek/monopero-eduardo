package br.com.lojagenerica.pdvclient.local;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Uma conexão só, guardada por instância (não mais um singleton estático
 * como o {@code Db} do legado — aquilo tinha um deadlock latente assim que
 * uma thread de sync em background existisse ao lado da EDT do Swing).
 * SQLite não é seguro pra escrita concorrente de qualquer forma, então toda
 * operação de mais de um statement (DAO fazendo insert composto) deve
 * segurar {@link #lock()} do início ao fim — ver {@code VendaLocalDao}.
 */
public final class LocalDb implements AutoCloseable {

    private final Connection connection;
    private final Object lock = new Object();

    public LocalDb(Path arquivo) throws SQLException {
        try {
            java.nio.file.Files.createDirectories(arquivo.getParent());
        } catch (java.io.IOException e) {
            throw new SQLException("Não foi possível criar a pasta de " + arquivo, e);
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + arquivo.toAbsolutePath());
        try (Statement pragma = connection.createStatement()) {
            pragma.execute("PRAGMA foreign_keys = ON;");
        }
        bootstrapSchema();
    }

    /** Objeto de sincronização pra operações multi-statement — ver javadoc da classe. */
    public Object lock() {
        return lock;
    }

    public Connection connection() {
        return connection;
    }

    private void bootstrapSchema() throws SQLException {
        try (Statement stat = connection.createStatement()) {
            stat.execute("""
                    CREATE TABLE IF NOT EXISTS cache_produto (
                        id                  INTEGER PRIMARY KEY,
                        nome                TEXT NOT NULL,
                        codigo_interno      TEXT,
                        preco_venda         REAL,
                        unidade_estoque_id  INTEGER,
                        controla_estoque    INTEGER NOT NULL DEFAULT 1,
                        status              TEXT,
                        atualizado_em       TEXT NOT NULL
                    );
                    """);
            stat.execute("CREATE INDEX IF NOT EXISTS idx_cache_produto_nome ON cache_produto(nome);");
            stat.execute("CREATE INDEX IF NOT EXISTS idx_cache_produto_codigo ON cache_produto(codigo_interno);");

            stat.execute("""
                    CREATE TABLE IF NOT EXISTS sync_cursor (
                        recurso     TEXT PRIMARY KEY,
                        cursor_valor TEXT
                    );
                    """);

            // Idempotência do lado do caixa: reenviar o mesmo evento_uuid (retry após queda de rede
            // no meio do envio) é sempre a MESMA linha, nunca uma nova — o servidor também dedupe por
            // esse uuid, então mesmo numa corrida rara a venda não duplica dos dois lados.
            stat.execute("""
                    CREATE TABLE IF NOT EXISTS sync_outbox (
                        id                    INTEGER PRIMARY KEY AUTOINCREMENT,
                        evento_uuid           TEXT NOT NULL UNIQUE,
                        tipo                  TEXT NOT NULL,
                        ocorrido_em           TEXT NOT NULL,
                        payload_json          TEXT NOT NULL,
                        status                TEXT NOT NULL DEFAULT 'PENDENTE',
                        tentativas            INTEGER NOT NULL DEFAULT 0,
                        proxima_tentativa_em  TEXT NOT NULL,
                        ultimo_erro           TEXT,
                        servidor_id           INTEGER,
                        criado_em             TEXT NOT NULL
                    );
                    """);
            stat.execute("CREATE INDEX IF NOT EXISTS idx_sync_outbox_pendentes ON sync_outbox(status, id);");

            // Espelho local da venda, desacoplado do status de sync — recibo e relatórios locais
            // funcionam mesmo antes do servidor confirmar o recebimento do evento.
            stat.execute("""
                    CREATE TABLE IF NOT EXISTS venda_local (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        venda_uuid  TEXT NOT NULL UNIQUE,
                        criada_em   TEXT NOT NULL,
                        total       REAL NOT NULL,
                        status      TEXT NOT NULL DEFAULT 'ABERTA'
                    );
                    """);
            stat.execute("""
                    CREATE TABLE IF NOT EXISTS venda_local_item (
                        id              INTEGER PRIMARY KEY AUTOINCREMENT,
                        venda_local_id  INTEGER NOT NULL REFERENCES venda_local(id),
                        produto_id      INTEGER NOT NULL,
                        produto_nome    TEXT NOT NULL,
                        quantidade      REAL NOT NULL,
                        unidade_id      INTEGER,
                        preco_unitario  REAL NOT NULL,
                        desconto_valor  REAL
                    );
                    """);
            stat.execute("""
                    CREATE TABLE IF NOT EXISTS venda_local_pagamento (
                        id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                        venda_local_id      INTEGER NOT NULL REFERENCES venda_local(id),
                        forma_pagamento_id  INTEGER NOT NULL,
                        valor               REAL NOT NULL,
                        valor_recebido      REAL,
                        troco               REAL
                    );
                    """);
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
