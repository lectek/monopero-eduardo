package br.com.lojagenerica.pdvclient.local;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class FormaPagamentoCacheDao {

    private final LocalDb db;

    public FormaPagamentoCacheDao(LocalDb db) {
        this.db = db;
    }

    public void upsert(FormaPagamentoCache forma) throws SQLException {
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement("""
                    INSERT INTO cache_forma_pagamento (id, nome, natureza, afeta_caixa, ativo)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        nome = excluded.nome, natureza = excluded.natureza,
                        afeta_caixa = excluded.afeta_caixa, ativo = excluded.ativo;
                    """)) {
                ps.setLong(1, forma.id());
                ps.setString(2, forma.nome());
                ps.setString(3, forma.natureza());
                ps.setInt(4, forma.afetaCaixa() ? 1 : 0);
                ps.setInt(5, forma.ativo() ? 1 : 0);
                ps.executeUpdate();
            }
        }
    }

    public List<FormaPagamentoCache> listarAtivas() throws SQLException {
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT * FROM cache_forma_pagamento WHERE ativo = 1 ORDER BY nome;")) {
                ResultSet rs = ps.executeQuery();
                List<FormaPagamentoCache> formas = new ArrayList<>();
                while (rs.next()) {
                    formas.add(new FormaPagamentoCache(rs.getLong("id"), rs.getString("nome"),
                            rs.getString("natureza"), rs.getInt("afeta_caixa") == 1, rs.getInt("ativo") == 1));
                }
                return formas;
            }
        }
    }
}
