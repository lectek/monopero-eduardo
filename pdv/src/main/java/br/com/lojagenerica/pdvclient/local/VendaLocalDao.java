package br.com.lojagenerica.pdvclient.local;

import br.com.lojagenerica.pdvclient.shared.TipoEventoPdv;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Confirma uma venda no caixa: grava o espelho local (recibo/relatório
 * offline) e enfileira o evento de sync na MESMA transação SQLite — uma
 * venda nunca existe sem seu evento de outbox correspondente (ver
 * plano, §6). O uuid da venda vira o {@code evento_uuid} do outbox: é a
 * mesma chave de idempotência dos dois lados (espelha
 * {@code VendaRegistradaPayload}/{@code EventoPushRequest} no servidor).
 */
public final class VendaLocalDao {

    private final LocalDb db;
    private final OutboxDao outboxDao;

    public VendaLocalDao(LocalDb db, OutboxDao outboxDao) {
        this.db = db;
        this.outboxDao = outboxDao;
    }

    public UUID registrarVenda(Long localEstoqueId, Long clienteId, Long usuarioId, Double descontoValor,
                                List<ItemVendaLocal> itens, List<PagamentoVendaLocal> pagamentos) throws SQLException {
        UUID uuid = UUID.randomUUID();
        double total = itens.stream().mapToDouble(ItemVendaLocal::subtotal).sum() - (descontoValor == null ? 0 : descontoValor);
        String payloadJson = construirPayloadVendaRegistrada(localEstoqueId, clienteId, usuarioId, descontoValor, itens, pagamentos);

        synchronized (db.lock()) {
            Connection conn = db.connection();
            boolean autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                long vendaLocalId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO venda_local (venda_uuid, criada_em, total, status) VALUES (?, ?, ?, 'ABERTA');",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, Instant.now().toString());
                    ps.setDouble(3, total);
                    ps.executeUpdate();
                    ResultSet chaves = ps.getGeneratedKeys();
                    chaves.next();
                    vendaLocalId = chaves.getLong(1);
                }

                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO venda_local_item (venda_local_id, produto_id, produto_nome, quantidade,
                                                       unidade_id, preco_unitario, desconto_valor)
                        VALUES (?, ?, ?, ?, ?, ?, ?);
                        """)) {
                    for (ItemVendaLocal item : itens) {
                        ps.setLong(1, vendaLocalId);
                        ps.setLong(2, item.produtoId());
                        ps.setString(3, item.produtoNome());
                        ps.setDouble(4, item.quantidade());
                        if (item.unidadeId() == null) {
                            ps.setNull(5, java.sql.Types.INTEGER);
                        } else {
                            ps.setLong(5, item.unidadeId());
                        }
                        ps.setDouble(6, item.precoUnitario());
                        if (item.descontoValor() == null) {
                            ps.setNull(7, java.sql.Types.REAL);
                        } else {
                            ps.setDouble(7, item.descontoValor());
                        }
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO venda_local_pagamento (venda_local_id, forma_pagamento_id, valor, valor_recebido, troco)
                        VALUES (?, ?, ?, ?, ?);
                        """)) {
                    for (PagamentoVendaLocal pagamento : pagamentos) {
                        ps.setLong(1, vendaLocalId);
                        ps.setLong(2, pagamento.formaPagamentoId());
                        ps.setDouble(3, pagamento.valor());
                        if (pagamento.valorRecebido() == null) {
                            ps.setNull(4, java.sql.Types.REAL);
                        } else {
                            ps.setDouble(4, pagamento.valorRecebido());
                        }
                        if (pagamento.troco() == null) {
                            ps.setNull(5, java.sql.Types.REAL);
                        } else {
                            ps.setDouble(5, pagamento.troco());
                        }
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                outboxDao.inserirLinha(conn, uuid, TipoEventoPdv.VENDA_REGISTRADA, Instant.now(), payloadJson);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommitOriginal);
            }
        }
        return uuid;
    }

    /** Cancela localmente (best-effort, o servidor é quem decide de verdade) e enfileira o evento. */
    public void registrarCancelamento(UUID vendaUuid, String motivo, Long usuarioId) throws SQLException {
        JSONObject payload = new JSONObject();
        payload.put("vendaUuid", vendaUuid.toString());
        payload.put("motivo", motivo);
        if (usuarioId != null) {
            payload.put("usuarioId", usuarioId);
        }

        synchronized (db.lock()) {
            Connection conn = db.connection();
            boolean autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE venda_local SET status = 'CANCELADA' WHERE venda_uuid = ?;")) {
                    ps.setString(1, vendaUuid.toString());
                    ps.executeUpdate();
                }
                outboxDao.inserirLinha(conn, UUID.randomUUID(), TipoEventoPdv.VENDA_CANCELADA, Instant.now(), payload.toString());
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommitOriginal);
            }
        }
    }

    /** Campos e nomes espelham {@code br.com.lojagenerica.pdv.VendaRegistradaPayload} no servidor. */
    private String construirPayloadVendaRegistrada(Long localEstoqueId, Long clienteId, Long usuarioId, Double descontoValor,
                                                     List<ItemVendaLocal> itens, List<PagamentoVendaLocal> pagamentos) {
        JSONObject payload = new JSONObject();
        payload.put("localEstoqueId", localEstoqueId);
        payload.put("clienteId", clienteId);
        payload.put("usuarioId", usuarioId);
        payload.put("descontoValor", descontoValor);

        JSONArray itensJson = new JSONArray();
        for (ItemVendaLocal item : itens) {
            JSONObject itemJson = new JSONObject();
            itemJson.put("produtoId", item.produtoId());
            itemJson.put("quantidade", item.quantidade());
            itemJson.put("unidadeId", item.unidadeId());
            itemJson.put("precoUnitario", item.precoUnitario());
            itemJson.put("descontoValor", item.descontoValor());
            itensJson.put(itemJson);
        }
        payload.put("itens", itensJson);

        JSONArray pagamentosJson = new JSONArray();
        for (PagamentoVendaLocal pagamento : pagamentos) {
            JSONObject pagamentoJson = new JSONObject();
            pagamentoJson.put("formaPagamentoId", pagamento.formaPagamentoId());
            pagamentoJson.put("valor", pagamento.valor());
            pagamentoJson.put("valorRecebido", pagamento.valorRecebido());
            pagamentoJson.put("troco", pagamento.troco());
            pagamentosJson.put(pagamentoJson);
        }
        payload.put("pagamentos", pagamentosJson);

        return payload.toString();
    }
}
