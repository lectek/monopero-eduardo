package mysquare.core;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Db {
	
	private static Connection conn = null;
	
	private static Connection connect() {
	    try {    
	    	if(conn == null){
	    	    //Class.forName("org.sqlite.JDBC");
				Utility u =new Utility();
	    	    HashMap<String, String> properties = u.getProperties();
	            conn = DriverManager.getConnection(properties.get("dbDriver")+properties.get("dbSource"));
	            migrateProductsTable(conn);
	            migrateSoldRecordsTable(conn);
	            backfillSoldRecordsPrices(conn);
	            System.out.println("Connection to Database has been established.");
	        }
	    } catch (SQLException | IOException e) {
	            System.out.println(e.getMessage());
	    }
	    return conn;
	}

	private static void migrateProductsTable(Connection conn) throws SQLException {
		addColumnIfMissing(conn, "products", "pcode", "TEXT");
		addColumnIfMissing(conn, "products", "pdesc", "TEXT");
		addColumnIfMissing(conn, "products", "pprice", "REAL");
	}

	/** pprice on sold_records is the unit price at the moment of sale; NULL for older/non-priced dispatches. */
	private static void migrateSoldRecordsTable(Connection conn) throws SQLException {
		addColumnIfMissing(conn, "sold_records", "pprice", "REAL");
	}

	/**
	 * One-time, idempotent backfill for sales made before pprice existed: fills each still-NULL
	 * sold_records.pprice with that product's current catalog price, when known. This is an
	 * approximation (the real price at the time of that old sale may have been different) —
	 * it only runs on rows that still have NULL, so it never overwrites a price actually
	 * recorded at sale time, and it's a no-op once every matchable row has been filled.
	 */
	private static void backfillSoldRecordsPrices(Connection conn) throws SQLException {
		Statement stat = conn.createStatement();
		stat.executeUpdate(
				"UPDATE sold_records SET pprice = (" +
				"  SELECT p.pprice FROM products p " +
				"  WHERE p.pname = sold_records.product AND p.pclr = sold_records.colour AND p.pwt = sold_records.weight" +
				") WHERE pprice IS NULL AND EXISTS (" +
				"  SELECT 1 FROM products p WHERE p.pname = sold_records.product AND p.pclr = sold_records.colour " +
				"  AND p.pwt = sold_records.weight AND p.pprice IS NOT NULL" +
				");");
	}

	private static void addColumnIfMissing(Connection conn, String tableName, String columnName, String columnType) throws SQLException {
		if (!columnExists(conn, tableName, columnName)) {
			Statement stat = conn.createStatement();
			stat.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType + ";");
			stat.close();
		}
	}

	private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
		Statement stat = conn.createStatement();
		ResultSet rs = stat.executeQuery("PRAGMA table_info(" + tableName + ");");
		while (rs.next()) {
			if (columnName.equalsIgnoreCase(rs.getString("name"))) {
				rs.close();
				stat.close();
				return true;
			}
		}
		rs.close();
		stat.close();
		return false;
	}
	
	public static ResultSet fetchData(String tableName) throws SQLException {
		ResultSet rs = null;
		Connection conn = connect();
		Statement stat = conn.createStatement();
		if("products".equalsIgnoreCase(tableName))
			rs = stat.executeQuery("SELECT * FROM "+tableName+" ORDER BY pname;");
		else
			rs = stat.executeQuery("SELECT * FROM "+tableName+" ORDER BY timestamp DESC;");
		return rs;	
	}
	
	/** Inserts value into table.columnName only if it isn't already there; safe to call with an existing value. */
	public static void ensureListItem(String table, String columnName, String value) throws SQLException {
		Connection conn = connect();
		addListItemIfMissing(conn, table, columnName, value);
	}

	public static ResultSet fetchProducts() throws SQLException {
		Connection conn = connect();
		Statement stat = conn.createStatement();
		return stat.executeQuery("SELECT pname, pclr, pwt, pqt, pcode, pdesc, pprice FROM products ORDER BY pname;");
	}

	public static ResultSet fetchProduct(String product, String colour, String weight) throws SQLException {
		Connection conn = connect();
		PreparedStatement ps = conn.prepareStatement("SELECT pname, pclr, pwt, pqt, pcode, pdesc, pprice FROM products WHERE pname=? AND pclr=? AND pwt=?;");
		ps.setString(1, product);
		ps.setString(2, colour);
		ps.setString(3, weight);
		return ps.executeQuery();
	}

	/** Looks up a product by barcode, for the Sale screen's scanner field. */
	public static ResultSet fetchProductByCode(String code) throws SQLException {
		Connection conn = connect();
		PreparedStatement ps = conn.prepareStatement("SELECT pname, pclr, pwt FROM products WHERE pcode=?;");
		ps.setString(1, code);
		return ps.executeQuery();
	}

	/** Distinct colours this product name is currently stocked in, for the Sale screen's dependent dropdowns. */
	public static ArrayList<String> fetchColoursForProduct(String product) {
		ArrayList<String> colours = new ArrayList<String>();
		Connection conn = connect();
		try {
			PreparedStatement ps = conn.prepareStatement("SELECT DISTINCT pclr FROM products WHERE pname=? ORDER BY pclr;");
			ps.setString(1, product);
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				colours.add(rs.getString("pclr"));
			}
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
		return colours;
	}

	/** Distinct weights this product (optionally narrowed by colour) is currently stocked in. */
	public static ArrayList<String> fetchWeightsForProduct(String product, String colour) {
		ArrayList<String> weights = new ArrayList<String>();
		Connection conn = connect();
		try {
			PreparedStatement ps = colour == null
					? conn.prepareStatement("SELECT DISTINCT pwt FROM products WHERE pname=? ORDER BY pwt;")
					: conn.prepareStatement("SELECT DISTINCT pwt FROM products WHERE pname=? AND pclr=? ORDER BY pwt;");
			ps.setString(1, product);
			if (colour != null) {
				ps.setString(2, colour);
			}
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				weights.add(rs.getString("pwt"));
			}
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
		return weights;
	}

	public static void updateProduct(String oldProduct, String oldColour, String oldWeight, String newProduct,
								 String newColour, String newWeight, String code, String description, double price) throws Exception {
		updateProduct(oldProduct, oldColour, oldWeight, newProduct, newColour, newWeight, code, description, price, null);
	}

	/** Same as the 9-arg overload, but also sets pqt to an absolute value when qty is non-null. */
	public static void updateProduct(String oldProduct, String oldColour, String oldWeight, String newProduct,
								 String newColour, String newWeight, String code, String description, double price,
								 Integer qty) throws Exception {
		Connection conn = connect();
		boolean oldAutoCommit = conn.getAutoCommit();
		conn.setAutoCommit(false);
		try {
			PreparedStatement findCurrent = conn.prepareStatement("SELECT pqt FROM products WHERE pname=? AND pclr=? AND pwt=?;");
			findCurrent.setString(1, oldProduct);
			findCurrent.setString(2, oldColour);
			findCurrent.setString(3, oldWeight);
			ResultSet current = findCurrent.executeQuery();
			if (!current.next()) {
				throw new Exception("Product not found.");
			}
			current.close();
			findCurrent.close();

			boolean keyChanged = !oldProduct.equals(newProduct) || !oldColour.equals(newColour) || !oldWeight.equals(newWeight);
			if (keyChanged) {
				PreparedStatement findDuplicate = conn.prepareStatement("SELECT 1 FROM products WHERE pname=? AND pclr=? AND pwt=?;");
				findDuplicate.setString(1, newProduct);
				findDuplicate.setString(2, newColour);
				findDuplicate.setString(3, newWeight);
				ResultSet duplicate = findDuplicate.executeQuery();
				if (duplicate.next()) {
					duplicate.close();
					findDuplicate.close();
					throw new Exception("Another product already exists with the same name, colour and weight.");
				}
				duplicate.close();
				findDuplicate.close();
			}

			addListItemIfMissing(conn, "product_list", "pname", newProduct);
			addListItemIfMissing(conn, "colour_list", "pclr", newColour);
			addListItemIfMissing(conn, "weight_list", "pwt", newWeight);

			String sql = qty == null
					? "UPDATE products SET pname=?, pclr=?, pwt=?, pcode=?, pdesc=?, pprice=? WHERE pname=? AND pclr=? AND pwt=?;"
					: "UPDATE products SET pname=?, pclr=?, pwt=?, pcode=?, pdesc=?, pprice=?, pqt=? WHERE pname=? AND pclr=? AND pwt=?;";
			PreparedStatement update = conn.prepareStatement(sql);
			update.setString(1, newProduct);
			update.setString(2, newColour);
			update.setString(3, newWeight);
			update.setString(4, code);
			update.setString(5, description);
			update.setDouble(6, price);
			if (qty == null) {
				update.setString(7, oldProduct);
				update.setString(8, oldColour);
				update.setString(9, oldWeight);
			} else {
				update.setInt(7, qty);
				update.setString(8, oldProduct);
				update.setString(9, oldColour);
				update.setString(10, oldWeight);
			}
			update.executeUpdate();
			update.close();
			conn.commit();
		} catch (Exception e) {
			conn.rollback();
			throw e;
		} finally {
			conn.setAutoCommit(oldAutoCommit);
		}
	}

	public static void deleteProduct(String product, String colour, String weight) throws SQLException {
		Connection conn = connect();
		PreparedStatement ps = conn.prepareStatement("DELETE FROM products WHERE pname=? AND pclr=? AND pwt=?;");
		ps.setString(1, product);
		ps.setString(2, colour);
		ps.setString(3, weight);
		ps.executeUpdate();
	}

	private static void addListItemIfMissing(Connection conn, String tableName, String columnName, String value) throws SQLException {
		PreparedStatement find = conn.prepareStatement("SELECT 1 FROM " + tableName + " WHERE " + columnName + "=?;");
		find.setString(1, value);
		ResultSet rs = find.executeQuery();
		if (!rs.next()) {
			PreparedStatement insert = conn.prepareStatement("INSERT INTO " + tableName + " VALUES (?);");
			insert.setString(1, value);
			insert.executeUpdate();
			insert.close();
		}
		rs.close();
		find.close();
	}
	
	public static ResultSet addProduct(String product, String colour, String weight, int qty) throws Exception{
		Connection conn = connect();
		ResultSet rs = null;
		PreparedStatement ps1 = conn.prepareStatement("SELECT * FROM products WHERE pname=? AND pclr=? AND pwt=?;");
		ps1.setString(1, product);
		ps1.setString(2, colour);
		ps1.setString(3, weight);
		rs = ps1.executeQuery();
	          
		if (rs.next() == false) {
			PreparedStatement ps2 = conn.prepareStatement("INSERT INTO products (pname, pclr, pwt, pqt, pcode, pdesc, pprice) VALUES (?,?,?,?,?,?,?);");
			ps2.setString(1, product);
			ps2.setString(2, colour);
			ps2.setString(3, weight);
			ps2.setInt(4, qty);
			ps2.setString(5, "");
			ps2.setString(6, "");
			ps2.setDouble(7, 0);
			ps2.executeUpdate();	
		} else {
			int updtdQty = Integer.parseInt(rs.getString("pqt")) + qty;
			PreparedStatement ps3 = conn.prepareStatement("UPDATE products SET pqt=? WHERE pname=? AND pclr=? AND pwt=?;");
			ps3.setInt(1, updtdQty);
			ps3.setString(2, product);
			ps3.setString(3, colour);
			ps3.setString(4, weight);
			ps3.executeUpdate();
		}

		PreparedStatement ps4 = conn.prepareStatement("INSERT INTO prod_records (timestamp, product, colour, weight, quantity) VALUES (strftime('%d/%m/%Y %H:%M:%S','now','localtime'),?,?,?,?);");
		ps4.setString(1, product);
		ps4.setString(2, colour);
		ps4.setString(3, weight);
		ps4.setInt(4, qty);
		ps4.executeUpdate();
		Statement s1 = conn.createStatement();
		rs = s1.executeQuery("SELECT * FROM prod_records ORDER BY timestamp DESC;");

		return rs;
	}

	/** One line of a checkout sale, used by {@link #sellProducts}. */
	public static class SaleItem {
		public final String product;
		public final String colour;
		public final String weight;
		public final int qty;
		public final Double price;

		public SaleItem(String product, String colour, String weight, int qty, Double price) {
			this.product = product;
			this.colour = colour;
			this.weight = weight;
			this.qty = qty;
			this.price = price;
		}
	}

	/** Deducts stock for every line of a sale in one transaction, so a mid-sale failure leaves nothing half-applied. */
	public static void sellProducts(List<SaleItem> items) throws Exception {
		Connection conn = connect();
		boolean oldAutoCommit = conn.getAutoCommit();
		conn.setAutoCommit(false);
		try {
			for (SaleItem item : items) {
				PreparedStatement findCurrent = conn.prepareStatement("SELECT pqt FROM products WHERE pname=? AND pclr=? AND pwt=?;");
				findCurrent.setString(1, item.product);
				findCurrent.setString(2, item.colour);
				findCurrent.setString(3, item.weight);
				ResultSet current = findCurrent.executeQuery();
				if (!current.next()) {
					throw new Exception("Produto não encontrado: " + item.product);
				}
				int updatedQty = Integer.parseInt(current.getString("pqt")) - item.qty;
				current.close();
				findCurrent.close();

				PreparedStatement update = conn.prepareStatement("UPDATE products SET pqt=? WHERE pname=? AND pclr=? AND pwt=?;");
				update.setInt(1, updatedQty);
				update.setString(2, item.product);
				update.setString(3, item.colour);
				update.setString(4, item.weight);
				update.executeUpdate();
				update.close();

				PreparedStatement record = conn.prepareStatement(
						"INSERT INTO sold_records (timestamp, product, colour, weight, quantity, pprice) "
						+ "VALUES (strftime('%d/%m/%Y %H:%M:%S','now','localtime'),?,?,?,?,?);");
				record.setString(1, item.product);
				record.setString(2, item.colour);
				record.setString(3, item.weight);
				record.setInt(4, item.qty);
				if (item.price == null) {
					record.setNull(5, java.sql.Types.REAL);
				} else {
					record.setDouble(5, item.price);
				}
				record.executeUpdate();
				record.close();
			}
			conn.commit();
		} catch (Exception e) {
			conn.rollback();
			throw e;
		} finally {
			conn.setAutoCommit(oldAutoCommit);
		}
	}

	public static ResultSet sellProduct(String product, String colour, String weight, int qty) throws Exception {
		return sellProduct(product, colour, weight, qty, null);
	}

	/** Same as the 4-arg overload, but also records the unit price at the time of sale (pass null when there isn't one, e.g. a manual dispatch). */
	public static ResultSet sellProduct(String product, String colour, String weight, int qty, Double price) throws Exception{
		Connection conn = connect();
		ResultSet rs = null;

		PreparedStatement ps1 = conn.prepareStatement("SELECT * FROM products WHERE pname=? AND pclr=? AND pwt=?;");
        ps1.setString(1, product);
		ps1.setString(2, colour);
		ps1.setString(3, weight);
		rs = ps1.executeQuery();

		if (!rs.next()) {
			throw new Exception("Produto não encontrado.");
		} else {
			int updtdQty = Integer.parseInt(rs.getString("pqt")) - qty;
			PreparedStatement ps2 = conn.prepareStatement("UPDATE products SET pqt=? WHERE pname=? AND pclr=? AND pwt=?;");
			ps2.setInt(1, updtdQty);
			ps2.setString(2, product);
			ps2.setString(3, colour);
			ps2.setString(4, weight);
			ps2.executeUpdate();
		}

		PreparedStatement ps3 = conn.prepareStatement(
				"INSERT INTO sold_records (timestamp, product, colour, weight, quantity, pprice) "
				+ "VALUES (strftime('%d/%m/%Y %H:%M:%S','now','localtime'),?,?,?,?,?);");
		ps3.setString(1, product);
		ps3.setString(2, colour);
		ps3.setString(3, weight);
		ps3.setInt(4, qty);
		if (price == null) {
			ps3.setNull(5, java.sql.Types.REAL);
		} else {
			ps3.setDouble(5, price);
		}
		ps3.executeUpdate();
		Statement s1 = conn.createStatement();
		rs = s1.executeQuery("SELECT * FROM sold_records ORDER BY timestamp DESC;");

		return rs;
	}

	/** One row per calendar day that had at least one sale/dispatch: day, line count, total units, total value (0 where price wasn't recorded). */
	public static ResultSet fetchSalesByDate() throws SQLException {
		Connection conn = connect();
		Statement stat = conn.createStatement();
		return stat.executeQuery(
				"SELECT substr(timestamp,1,10) AS dia, "
				+ "COUNT(*) AS vendas, "
				+ "SUM(quantity) AS itens, "
				+ "SUM(quantity * COALESCE(pprice,0)) AS total "
				+ "FROM sold_records "
				+ "GROUP BY dia "
				// dia is dd/MM/yyyy; reorder to yyyy-MM-dd so DESC sorts chronologically, not lexicographically.
				+ "ORDER BY substr(dia,7,4) || substr(dia,4,2) || substr(dia,1,2) DESC;");
	}

	/**
	 * One entry per day-of-month (1-31) that had at least one sale/dispatch in the given
	 * month/year: {vendas, itens, total}. A Map (not a ResultSet, unlike the rest of this class)
	 * because the calendar screen needs O(1) lookup per day while drawing up to 31 grid cells.
	 */
	public static Map<Integer, double[]> fetchMonthSummary(int year, int month) throws SQLException {
		Connection conn = connect();
		PreparedStatement ps = conn.prepareStatement(
				"SELECT CAST(substr(timestamp,1,2) AS INTEGER) AS dia, "
				+ "COUNT(*) AS vendas, "
				+ "SUM(quantity) AS itens, "
				+ "SUM(quantity * COALESCE(pprice,0)) AS total "
				+ "FROM sold_records "
				+ "WHERE substr(timestamp,4,2)=? AND substr(timestamp,7,4)=? "
				+ "GROUP BY dia;");
		ps.setString(1, String.format("%02d", month));
		ps.setString(2, String.format("%04d", year));
		ResultSet rs = ps.executeQuery();
		Map<Integer, double[]> byDay = new TreeMap<Integer, double[]>();
		while (rs.next()) {
			byDay.put(rs.getInt("dia"), new double[]{rs.getInt("vendas"), rs.getInt("itens"), rs.getDouble("total")});
		}
		return byDay;
	}

	/** Every sold_records line for one exact calendar day ("dd/MM/yyyy"), oldest first. */
	public static ResultSet fetchSalesForDay(String diaBr) throws SQLException {
		Connection conn = connect();
		PreparedStatement ps = conn.prepareStatement(
				"SELECT timestamp, product, colour, weight, quantity, pprice FROM sold_records "
				+ "WHERE substr(timestamp,1,10)=? ORDER BY timestamp;");
		ps.setString(1, diaBr);
		return ps.executeQuery();
	}

	/** Gross revenue (quantity * pprice, unpriced dispatches count as 0) for today, this month, this year and all time. */
	public static class RevenueSummary {
		public final double hoje, mes, ano, total;
		RevenueSummary(double hoje, double mes, double ano, double total) {
			this.hoje = hoje;
			this.mes = mes;
			this.ano = ano;
			this.total = total;
		}
	}

	public static RevenueSummary fetchRevenueSummary() throws SQLException {
		Connection conn = connect();
		Statement stat = conn.createStatement();
		// dd/MM/yyyy -> yyyy-MM-dd once in a subquery, then compared against SQLite's own date/strftime output.
		ResultSet rs = stat.executeQuery(
				"SELECT "
				+ "SUM(CASE WHEN iso = date('now','localtime') THEN valor ELSE 0 END) AS hoje, "
				+ "SUM(CASE WHEN substr(iso,1,7) = strftime('%Y-%m','now','localtime') THEN valor ELSE 0 END) AS mes, "
				+ "SUM(CASE WHEN substr(iso,1,4) = strftime('%Y','now','localtime') THEN valor ELSE 0 END) AS ano, "
				+ "SUM(valor) AS total "
				+ "FROM (SELECT quantity * COALESCE(pprice,0) AS valor, "
				+ "substr(timestamp,7,4) || '-' || substr(timestamp,4,2) || '-' || substr(timestamp,1,2) AS iso "
				+ "FROM sold_records);");
		if (rs.next()) {
			return new RevenueSummary(rs.getDouble("hoje"), rs.getDouble("mes"), rs.getDouble("ano"), rs.getDouble("total"));
		}
		return new RevenueSummary(0, 0, 0, 0);
	}
	
	public static ArrayList<String> fetchPList() {
		ResultSet rs = null;
		Connection conn = connect();
		ArrayList<String> pal = new ArrayList<String>();
		
		try {
			Statement s1 = conn.createStatement();
			rs = s1.executeQuery("SELECT * FROM product_list ORDER BY pname;");
			while(rs.next() != false) {
				pal.add(rs.getString("pname"));
			}
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
		
		return pal;
	}
	
	public static ArrayList<String> fetchCList() {
		ResultSet rs = null;
		Connection conn = connect();
		ArrayList<String> cal = new ArrayList<String>();
		
		try {
			Statement s1 = conn.createStatement();
			rs = s1.executeQuery("SELECT * FROM colour_list ORDER BY pclr;");
			while(rs.next() != false) {
				cal.add(rs.getString("pclr"));
			}
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
		
		return cal;
	}
	
	public static ArrayList<String> fetchWList() {
		ResultSet rs = null;
		Connection conn = connect();
		ArrayList<String> wal = new ArrayList<String>();

		try {
			Statement s1 = conn.createStatement();
			rs = s1.executeQuery("SELECT * FROM weight_list ORDER BY pwt;");
			while(rs.next() != false) {
				wal.add(rs.getString("pwt"));
			}
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}

		return wal;
	}

	/**
	 * Acesso administrativo do site (SaaS): tabela admin_users vive na mesma rbp.db,
	 * criada pelo SaaS na primeira vez que ele roda. O IMS é o único lugar que cria/
	 * atualiza esses logins — o SaaS não se auto-cadastra, só o dono da loja, por aqui.
	 * A mesma tabela cobre dois papéis: role="ADMIN" (painel administrativo) e
	 * role="MOTOBOY" (área de entregas no celular, com comissão sobre o frete).
	 */
	public static class AdminSaasAccount {
		public final String email;
		public final String role;
		public final Double percentualComissao;

		AdminSaasAccount(String email, String role, Double percentualComissao) {
			this.email = email;
			this.role = role;
			this.percentualComissao = percentualComissao;
		}
	}

	public static ArrayList<AdminSaasAccount> fetchAdminAccounts() {
		ArrayList<AdminSaasAccount> out = new ArrayList<AdminSaasAccount>();
		Connection conn = connect();
		try {
			Statement s = conn.createStatement();
			ResultSet rs = s.executeQuery("SELECT email, role, percentual_comissao FROM admin_users ORDER BY id;");
			while (rs.next()) {
				double comissao = rs.getDouble("percentual_comissao");
				out.add(new AdminSaasAccount(
						rs.getString("email"),
						rs.getString("role"),
						rs.wasNull() ? null : comissao
				));
			}
		} catch (SQLException e) {
			// Tabela ainda não existe (SaaS nunca rodou): tela mostra vazio, não trava o IMS.
			System.out.println(e.getMessage());
		}
		return out;
	}

	/**
	 * Cria o acesso se o e-mail ainda não existe, ou atualiza nome/senha/role/comissão
	 * se já existir. percentualComissao só faz sentido pra role="MOTOBOY" — passe null
	 * pra "ADMIN".
	 */
	public static void salvarAdminSaas(String nome, String email, String senhaHash, String role, Double percentualComissao) throws SQLException {
		Connection conn = connect();
		PreparedStatement ps = conn.prepareStatement(
				"INSERT INTO admin_users (nome, email, senha_hash, role, ativo, percentual_comissao) VALUES (?, ?, ?, ?, 1, ?) "
				+ "ON CONFLICT(email) DO UPDATE SET nome = excluded.nome, senha_hash = excluded.senha_hash, "
				+ "role = excluded.role, percentual_comissao = excluded.percentual_comissao, ativo = 1;");
		ps.setString(1, nome);
		ps.setString(2, email);
		ps.setString(3, senhaHash);
		ps.setString(4, role);
		if (percentualComissao == null) {
			ps.setNull(5, java.sql.Types.REAL);
		} else {
			ps.setDouble(5, percentualComissao);
		}
		ps.executeUpdate();
	}
}
