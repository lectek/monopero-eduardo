package br.com.lojagenerica.pdvclient.local;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ProdutoCacheDao {

    private final LocalDb db;

    public ProdutoCacheDao(LocalDb db) {
        this.db = db;
    }

    /** Upsert por id — é assim que o pull incremental aplica tanto produto novo quanto alterado. */
    public void upsert(ProdutoCache produto) throws SQLException {
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement("""
                    INSERT INTO cache_produto (id, nome, codigo_interno, preco_venda, unidade_estoque_id,
                                                controla_estoque, status, atualizado_em)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        nome = excluded.nome, codigo_interno = excluded.codigo_interno,
                        preco_venda = excluded.preco_venda, unidade_estoque_id = excluded.unidade_estoque_id,
                        controla_estoque = excluded.controla_estoque, status = excluded.status,
                        atualizado_em = excluded.atualizado_em;
                    """)) {
                ps.setLong(1, produto.id());
                ps.setString(2, produto.nome());
                ps.setString(3, produto.codigoInterno());
                if (produto.precoVenda() == null) {
                    ps.setNull(4, java.sql.Types.REAL);
                } else {
                    ps.setDouble(4, produto.precoVenda());
                }
                if (produto.unidadeEstoqueId() == null) {
                    ps.setNull(5, java.sql.Types.INTEGER);
                } else {
                    ps.setLong(5, produto.unidadeEstoqueId());
                }
                ps.setInt(6, produto.controlaEstoque() ? 1 : 0);
                ps.setString(7, produto.status());
                ps.setString(8, produto.atualizadoEm().toString());
                ps.executeUpdate();
            }
        }
    }

    public List<ProdutoCache> buscarPorNomeOuCodigo(String termo) throws SQLException {
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT * FROM cache_produto WHERE nome LIKE ? OR codigo_interno = ? ORDER BY nome LIMIT 50;")) {
                ps.setString(1, "%" + termo + "%");
                ps.setString(2, termo);
                return mapear(ps.executeQuery());
            }
        }
    }

    /** Leitor de código de barras: um código bipado é sempre exato, nunca prefixo/substring. */
    public ProdutoCache buscarPorCodigoExato(String codigo) throws SQLException {
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT * FROM cache_produto WHERE codigo_interno = ? LIMIT 1;")) {
                ps.setString(1, codigo);
                List<ProdutoCache> resultado = mapear(ps.executeQuery());
                return resultado.isEmpty() ? null : resultado.get(0);
            }
        }
    }

    public ProdutoCache buscarPorId(long id) throws SQLException {
        synchronized (db.lock()) {
            try (PreparedStatement ps = db.connection().prepareStatement("SELECT * FROM cache_produto WHERE id = ?;")) {
                ps.setLong(1, id);
                List<ProdutoCache> resultado = mapear(ps.executeQuery());
                return resultado.isEmpty() ? null : resultado.get(0);
            }
        }
    }

    private List<ProdutoCache> mapear(ResultSet rs) throws SQLException {
        List<ProdutoCache> produtos = new ArrayList<>();
        while (rs.next()) {
            double preco = rs.getDouble("preco_venda");
            Double precoVenda = rs.wasNull() ? null : preco;
            long unidade = rs.getLong("unidade_estoque_id");
            Long unidadeId = rs.wasNull() ? null : unidade;
            produtos.add(new ProdutoCache(
                    rs.getLong("id"),
                    rs.getString("nome"),
                    rs.getString("codigo_interno"),
                    precoVenda,
                    unidadeId,
                    rs.getInt("controla_estoque") == 1,
                    rs.getString("status"),
                    Instant.parse(rs.getString("atualizado_em"))));
        }
        return produtos;
    }
}
