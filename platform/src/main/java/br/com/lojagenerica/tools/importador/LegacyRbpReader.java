package br.com.lojagenerica.tools.importador;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Leitura pura via JDBC do {@code rbp.db} legado — sem Spring, sem
 * dependência do restante do sistema, só {@code org.sqlite.JDBC}
 * (a única exceção permitida em todo o classpath de produção, ver
 * ArchitectureRulesTest). Nunca escreve nada neste arquivo.
 */
final class LegacyRbpReader {

    private LegacyRbpReader() {
    }

    static Connection abrir(Path arquivoRbp) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + arquivoRbp.toAbsolutePath());
    }

    static List<ProdutoLegado> lerProdutos(Connection conn) throws SQLException {
        List<ProdutoLegado> produtos = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("select pname, pclr, pwt, pqt, pcode, pdesc, pprice from products")) {
            while (rs.next()) {
                Double preco = rs.getObject("pprice") != null ? rs.getDouble("pprice") : null;
                produtos.add(new ProdutoLegado(
                        rs.getString("pname"), rs.getString("pclr"), rs.getString("pwt"),
                        rs.getString("pqt"), rs.getString("pcode"), rs.getString("pdesc"), preco));
            }
        }
        return produtos;
    }

    static List<AdminUserLegado> lerAdminUsers(Connection conn) throws SQLException {
        List<AdminUserLegado> usuarios = new ArrayList<>();
        // admin_users só existe se o SaaS já rodou alguma vez nessa loja —
        // uma loja nova (só IMS, nunca ligou o site) não tem essa tabela.
        if (!tabelaExiste(conn, "admin_users")) {
            return usuarios;
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("select nome, email, senha_hash, role, ativo from admin_users")) {
            while (rs.next()) {
                usuarios.add(new AdminUserLegado(
                        rs.getString("nome"), rs.getString("email"), rs.getString("senha_hash"),
                        rs.getString("role"), rs.getBoolean("ativo")));
            }
        }
        return usuarios;
    }

    private static boolean tabelaExiste(Connection conn, String nomeTabela) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "select name from sqlite_master where type='table' and name='" + nomeTabela + "'")) {
            return rs.next();
        }
    }
}
