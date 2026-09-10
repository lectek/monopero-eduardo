package br.com.lojagenerica.pdvclient.local;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Cursor de pull incremental por recurso ("produto", depois categoria/unidade/etc.). */
public final class SyncCursorDao {

    private final LocalDb db;

    public SyncCursorDao(LocalDb db) {
        this.db = db;
    }

    public String obter(String recurso) throws SQLException {
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT cursor_valor FROM sync_cursor WHERE recurso = ?;")) {
                ps.setString(1, recurso);
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getString("cursor_valor") : null;
            }
        }
    }

    public void salvar(String recurso, String cursor) throws SQLException {
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement("""
                    INSERT INTO sync_cursor (recurso, cursor_valor) VALUES (?, ?)
                    ON CONFLICT(recurso) DO UPDATE SET cursor_valor = excluded.cursor_valor;
                    """)) {
                ps.setString(1, recurso);
                ps.setString(2, cursor);
                ps.executeUpdate();
            }
        }
    }
}
