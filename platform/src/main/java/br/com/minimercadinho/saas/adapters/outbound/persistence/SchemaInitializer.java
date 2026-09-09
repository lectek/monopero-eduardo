package br.com.minimercadinho.saas.adapters.outbound.persistence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Prepara o mesmo arquivo rbp.db que o IMS já usa: garante as colunas que o
 * IMS adiciona sob demanda (pcode/pdesc/pprice em products, pprice em
 * sold_records — ver Db.java do IMS) e cria as tabelas novas que só o SaaS
 * usa. Tudo idempotente (IF NOT EXISTS / checagem de coluna), seguindo a
 * mesma convenção de migração leve que o IMS já adota em vez de Flyway
 * (SQLite tem suporte fraco a ALTER TABLE).
 */
@Component
@Order(0)
public class SchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final DataSource dataSource;

    public SchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            migrateImsColumns(conn);
            createNewTables(conn);
            migrateOwnColumns(conn);
        }
        log.info("Schema do SaaS verificado/atualizado em cima do rbp.db compartilhado com o IMS.");
    }

    /** Mesmas colunas que Db.migrateProductsTable/migrateSoldRecordsTable adicionam no IMS. */
    private void migrateImsColumns(Connection conn) throws SQLException {
        addColumnIfMissing(conn, "products", "pcode", "TEXT");
        addColumnIfMissing(conn, "products", "pdesc", "TEXT");
        addColumnIfMissing(conn, "products", "pprice", "REAL");
        addColumnIfMissing(conn, "sold_records", "pprice", "REAL");
    }

    /**
     * Colunas novas em tabelas que já existiam antes do recurso de motoboy:
     * percentual_comissao (só usado quando admin_users.role = 'MOTOBOY') e
     * valor_frete (pra calcular quanto o motoboy recebe: percentual * frete
     * dos pedidos que ele entregou — antes só o total do pedido era salvo,
     * frete e itens ficavam somados sem separação).
     */
    private void migrateOwnColumns(Connection conn) throws SQLException {
        addColumnIfMissing(conn, "admin_users", "percentual_comissao", "REAL");
        addColumnIfMissing(conn, "pedido", "valor_frete", "REAL");
    }

    private void addColumnIfMissing(Connection conn, String table, String column, String type) throws SQLException {
        if (columnExists(conn, table, column)) {
            return;
        }
        try (Statement stat = conn.createStatement()) {
            stat.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type + ";");
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (Statement stat = conn.createStatement();
             ResultSet rs = stat.executeQuery("PRAGMA table_info(" + table + ");")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void createNewTables(Connection conn) throws SQLException {
        try (Statement stat = conn.createStatement()) {
            stat.executeUpdate("""
                CREATE TABLE IF NOT EXISTS admin_users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nome TEXT NOT NULL,
                    email TEXT NOT NULL UNIQUE,
                    senha_hash TEXT NOT NULL,
                    role TEXT NOT NULL DEFAULT 'ADMIN',
                    ativo INTEGER NOT NULL DEFAULT 1,
                    criado_em TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
                );
                """);

            // CustomerEntity: endereço é um campo texto único (mesmo modelo do UsuarioEntity
            // do ParaisoPet) — sem tabela de endereço separada.
            stat.executeUpdate("""
                CREATE TABLE IF NOT EXISTS customers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nome TEXT NOT NULL,
                    email TEXT NOT NULL UNIQUE,
                    cpf TEXT,
                    telefone TEXT,
                    endereco TEXT,
                    senha_hash TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    version INTEGER NOT NULL DEFAULT 0
                );
                """);

            // Espelha RefreshTokenEntity (portado do módulo JWT do ParaisoPet) — ddl-auto=none, então o schema é criado aqui.
            stat.executeUpdate("""
                CREATE TABLE IF NOT EXISTS refresh_tokens (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    tenant_id TEXT NOT NULL,
                    token TEXT NOT NULL UNIQUE,
                    issued_at TEXT NOT NULL,
                    expires_at TEXT NOT NULL,
                    revoked_at TEXT,
                    ip_address TEXT,
                    user_agent TEXT
                );
                """);
            stat.executeUpdate("CREATE INDEX IF NOT EXISTS ix_refresh_token_user_tenant ON refresh_tokens(user_id, tenant_id);");
            stat.executeUpdate("CREATE INDEX IF NOT EXISTS ix_refresh_token_expires_at ON refresh_tokens(expires_at);");

            // PedidoEntity (portado do ParaisoPet: mesmos campos de entrega +
            // gateway_* prontos para o checkout Mercado Pago).
            stat.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pedido (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    cliente_id INTEGER NOT NULL REFERENCES customers(id),
                    data TEXT NOT NULL,
                    total REAL NOT NULL,
                    status TEXT NOT NULL,
                    tipo_pagamento TEXT NOT NULL,
                    modo_entrega TEXT,
                    metodo_pagamento TEXT,
                    forma_pagamento_recebida TEXT,
                    pagamento_divergente INTEGER NOT NULL DEFAULT 0,
                    avaliacao_cliente INTEGER,
                    pagamento_recebido_em TEXT,
                    gateway_provider TEXT,
                    gateway_owner_reference TEXT,
                    gateway_preference_id TEXT,
                    gateway_external_reference TEXT,
                    gateway_checkout_url TEXT,
                    gateway_payment_id TEXT,
                    gateway_payment_status TEXT,
                    gateway_payment_status_detail TEXT,
                    gateway_payment_updated_at TEXT,
                    gateway_payment_ticket_url TEXT,
                    gateway_pix_qr_code TEXT,
                    gateway_pix_qr_code_base64 TEXT,
                    endereco_entrega TEXT,
                    codigo_entrega TEXT,
                    codigo_entrega_gerado_em TEXT,
                    codigo_entrega_confirmado_em TEXT,
                    cancelamento_motivo TEXT,
                    cancelado_em TEXT,
                    estoque_baixado INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    version INTEGER NOT NULL DEFAULT 0
                );
                """);
            stat.executeUpdate("CREATE INDEX IF NOT EXISTS ix_pedido_gateway_external_reference ON pedido(gateway_external_reference);");
            stat.executeUpdate("CREATE INDEX IF NOT EXISTS ix_pedido_gateway_payment_id ON pedido(gateway_payment_id);");

            // ItemPedidoEntity: snapshot do produto por chave natural (nome/cor/peso),
            // já que o produto real vive em products (rbp.db), sem ID numérico.
            stat.executeUpdate("""
                CREATE TABLE IF NOT EXISTS item_pedido (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    pedido_id INTEGER NOT NULL REFERENCES pedido(id),
                    produto_nome TEXT NOT NULL,
                    produto_cor TEXT,
                    produto_peso TEXT,
                    produto_codigo_barras TEXT,
                    quantidade INTEGER NOT NULL,
                    preco_unitario REAL NOT NULL
                );
                """);

            // EntregaRotaEntity — entregador/criadaPor apontam pra admin_users (equipe da
            // loja), não pra customers (diferente do ParaisoPet, que usa o mesmo Usuario
            // pros dois papéis — aqui separamos cliente de equipe, ver CustomerEntity).
            stat.executeUpdate("""
                CREATE TABLE IF NOT EXISTS entrega_rota (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    data_operacao TEXT NOT NULL,
                    origem TEXT NOT NULL,
                    distancia_total_km REAL NOT NULL DEFAULT 0,
                    custo_total REAL NOT NULL DEFAULT 0,
                    mapa_url TEXT,
                    status TEXT NOT NULL,
                    entregador_usuario_id INTEGER REFERENCES admin_users(id),
                    criada_por_usuario_id INTEGER REFERENCES admin_users(id),
                    despachada_em TEXT,
                    iniciada_em TEXT,
                    finalizada_em TEXT,
                    motorista_latitude REAL,
                    motorista_longitude REAL,
                    motorista_localizacao_em TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    version INTEGER NOT NULL DEFAULT 0
                );
                """);

            stat.executeUpdate("""
                CREATE TABLE IF NOT EXISTS entrega_parada (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    rota_id INTEGER NOT NULL REFERENCES entrega_rota(id),
                    pedido_id INTEGER NOT NULL REFERENCES pedido(id),
                    ordem_rota INTEGER NOT NULL,
                    cliente_nome_snapshot TEXT NOT NULL,
                    endereco_snapshot TEXT NOT NULL,
                    codigo_entrega_snapshot TEXT,
                    status TEXT NOT NULL,
                    distancia_anterior_km REAL NOT NULL DEFAULT 0,
                    distancia_acumulada_km REAL NOT NULL DEFAULT 0,
                    duracao_anterior_segundos INTEGER,
                    duracao_acumulada_segundos INTEGER,
                    latitude REAL,
                    longitude REAL,
                    confirmado_em TEXT,
                    motivo_falha TEXT,
                    observacao TEXT,
                    forma_pagamento_recebida TEXT,
                    pagamento_divergente INTEGER NOT NULL DEFAULT 0,
                    avaliacao_entrega INTEGER,
                    ocorrencias TEXT,
                    aproximando_notificado_em TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    version INTEGER NOT NULL DEFAULT 0
                );
                """);

            // ClienteNotificacaoEntity (customer_id, já renomeado do usuario_id original do ParaisoPet).
            stat.executeUpdate("""
                CREATE TABLE IF NOT EXISTS cliente_notificacao (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    customer_id INTEGER NOT NULL REFERENCES customers(id),
                    tipo TEXT,
                    titulo TEXT,
                    mensagem TEXT,
                    lida INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL
                );
                """);

            // AppSettingEntity — chave/valor genérico usado por AppSettingService/AppProps
            // (frete, horários de rota, branding etc.).
            stat.executeUpdate("""
                CREATE TABLE IF NOT EXISTS app_settings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    setting_key TEXT NOT NULL UNIQUE,
                    setting_value TEXT,
                    description TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                """);
        }
    }
}
