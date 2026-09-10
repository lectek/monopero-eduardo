package br.com.lojagenerica.pdvclient.local;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class LocalEstoqueCacheDao {

    private final LocalDb db;

    public LocalEstoqueCacheDao(LocalDb db) {
        this.db = db;
    }

    public void upsert(LocalEstoqueCache local) throws SQLException {
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement("""
                    INSERT INTO cache_local_estoque (id, nome, tipo, principal, ativo)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        nome = excluded.nome, tipo = excluded.tipo,
                        principal = excluded.principal, ativo = excluded.ativo;
                    """)) {
                ps.setLong(1, local.id());
                ps.setString(2, local.nome());
                ps.setString(3, local.tipo());
                ps.setInt(4, local.principal() ? 1 : 0);
                ps.setInt(5, local.ativo() ? 1 : 0);
                ps.executeUpdate();
            }
        }
    }

    public List<LocalEstoqueCache> listarAtivos() throws SQLException {
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT * FROM cache_local_estoque WHERE ativo = 1 ORDER BY nome;")) {
                ResultSet rs = ps.executeQuery();
                List<LocalEstoqueCache> locais = new ArrayList<>();
                while (rs.next()) {
                    locais.add(new LocalEstoqueCache(rs.getLong("id"), rs.getString("nome"),
                            rs.getString("tipo"), rs.getInt("principal") == 1, rs.getInt("ativo") == 1));
                }
                return locais;
            }
        }
    }
}
