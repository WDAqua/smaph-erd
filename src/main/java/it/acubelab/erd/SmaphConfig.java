package it.acubelab.erd;

import java.io.FileInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

public class SmaphConfig {
	private static String defaultBingKey;
	private static String defaultTagmeKey;
	private static String defaultTagmeHost;
	private static String configFile;

	/**Set the configuration file.
	 * @param filename the configuration file.
	 */
	public static void setConfigFile(String filename) {
		configFile = filename;
	}

	/**
	 * @return the default Tagme key.
	 */
	public static String getDefaultTagmeKey() {
		if (defaultTagmeKey == null)
			initialize();
		return defaultTagmeKey;
	}

	/**
	 * @return the default Tagme host.
	 */
	public static String getDefaultTagmeHost() {
		if (defaultTagmeHost == null)
			initialize();
		return defaultTagmeHost;
	}

	/**
	 * @return the default Bing key.
	 */
	public static String getDefaultBingKey() {
		if (defaultBingKey == null)
			initialize();
		return defaultBingKey;
	}

	/**
	 * Load data from file.
	 */
	private static void initialize() {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		Document doc;
		try {
			builder = factory.newDocumentBuilder();
			doc = builder.parse(new FileInputStream(configFile));
			defaultBingKey = getConfigValue("bing", "key", doc);
			defaultTagmeKey = getConfigValue("tagme", "key", doc);
			defaultTagmeHost = getConfigValue("tagme", "host", doc);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		if (defaultBingKey.isEmpty() || defaultBingKey.equals("KEY"))
			throw new RuntimeException(
					"Configuration file "
							+ configFile
							+ " has dummy value 'KEY' or is unset. Please replace with an actual bing key.");
		if (defaultTagmeKey.isEmpty() || defaultTagmeKey.equals("KEY"))
			throw new RuntimeException(
					"Configuration file "
							+ configFile
							+ " has dummy value 'KEY' or is unset. Please replace with an actual Tagme key.");
		if (defaultTagmeHost.isEmpty() || defaultTagmeHost.equals("KEY"))
			throw new RuntimeException(
					"Configuration file "
							+ configFile
							+ " has dummy value 'KEY' or is unset. Please replace with the actual Tagme host.");
	}

	private static String getConfigValue(String setting, String name,
			Document doc) throws XPathExpressionException {
		XPathFactory xPathfactory = XPathFactory.newInstance();
		XPath xpath = xPathfactory.newXPath();
		XPathExpression userExpr = xpath.compile("smaph/setting[@name=\""
				+ setting + "\"]/param[@name=\"" + name + "\"]/@value");
		return userExpr.evaluate(doc);
	}

}
