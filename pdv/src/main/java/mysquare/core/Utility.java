package mysquare.core;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

public class Utility {

	InputStream inputStream;

	public HashMap<String, String> getProperties() throws IOException {
		Properties prop = new Properties();
		HashMap<String, String> properties = new HashMap<String, String>();

		try {
			FileInputStream ip = new FileInputStream(configFilePath());
			if (ip != null) {
				prop.load(ip);
				System.out.println(System.getProperty("os.name"));
			} else {
				throw new FileNotFoundException("Property file not found.");
			}

			properties.put("dbSource", prop.getProperty("DB_PATH"));
			properties.put("dbDriver", prop.getProperty("DB_DRIVER"));
			properties.put("mpAccessToken", prop.getProperty("MP_ACCESS_TOKEN", ""));
			properties.put("mpPayerEmail", prop.getProperty("MP_PAYER_EMAIL_PADRAO", ""));
			properties.put("saasAdminUrl", prop.getProperty("SAAS_ADMIN_URL", ""));

		} catch (Exception e) {
			System.out.println("Exception: " + e);
		}
		return properties;
	}

	/**
	 * Rewrites MP_ACCESS_TOKEN and MP_PAYER_EMAIL_PADRAO in config.properties in place, leaving every
	 * other line (including DB_PATH/DB_DRIVER and comments) untouched. Adds the keys if they're missing.
	 */
	public void saveMercadoPagoCredentials(String accessToken, String payerEmail) throws IOException {
		String path = configFilePath();
		List<String> lines = new ArrayList<String>();
		BufferedReader reader = new BufferedReader(new FileReader(path));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
		} finally {
			reader.close();
		}

		boolean sawToken = false;
		boolean sawEmail = false;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line.startsWith("MP_ACCESS_TOKEN=")) {
				lines.set(i, "MP_ACCESS_TOKEN=" + accessToken);
				sawToken = true;
			} else if (line.startsWith("MP_PAYER_EMAIL_PADRAO=")) {
				lines.set(i, "MP_PAYER_EMAIL_PADRAO=" + payerEmail);
				sawEmail = true;
			}
		}
		if (!sawToken) {
			lines.add("MP_ACCESS_TOKEN=" + accessToken);
		}
		if (!sawEmail) {
			lines.add("MP_PAYER_EMAIL_PADRAO=" + payerEmail);
		}

		PrintWriter writer = new PrintWriter(new FileWriter(path));
		try {
			for (String line : lines) {
				writer.println(line);
			}
		} finally {
			writer.close();
		}
	}

	/** Rewrites SAAS_ADMIN_URL in config.properties in place, same approach as saveMercadoPagoCredentials. */
	public void saveSaasAdminUrl(String url) throws IOException {
		String path = configFilePath();
		List<String> lines = new ArrayList<String>();
		BufferedReader reader = new BufferedReader(new FileReader(path));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
		} finally {
			reader.close();
		}

		boolean sawUrl = false;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).startsWith("SAAS_ADMIN_URL=")) {
				lines.set(i, "SAAS_ADMIN_URL=" + url);
				sawUrl = true;
				break;
			}
		}
		if (!sawUrl) {
			lines.add("SAAS_ADMIN_URL=" + url);
		}

		PrintWriter writer = new PrintWriter(new FileWriter(path));
		try {
			for (String line : lines) {
				writer.println(line);
			}
		} finally {
			writer.close();
		}
	}

	/**
	 * Windows keeps the original fixed path the production install relies on.
	 * Any other OS (used for local dev/testing on Linux/macOS) reads from the
	 * user's home directory instead, since "C:/..." isn't a real path there.
	 */
	private static String configFilePath() {
		String os = System.getProperty("os.name", "").toLowerCase();
		if (os.contains("win")) {
			return "C:/ims_files/config.properties";
		}
		return System.getProperty("user.home") + "/ims_files/config.properties";
	}

	public String[] getProductList() {
		ArrayList<String> productList = Db.fetchPList();
	    String[] productArr = productList.toArray(new String[productList.size()]);
	    return productArr;
	}
	
	public String[] getColourList() {
		ArrayList<String> colourList = Db.fetchCList();
	    String[] colourArr = colourList.toArray(new String[colourList.size()]);
	    return colourArr;
   	}
	
	public String[] getWeightList() {
		ArrayList<String> weightList = Db.fetchWList();
		String[] weightArr = weightList.toArray(new String[weightList.size()]);
		return weightArr;
	}
}
