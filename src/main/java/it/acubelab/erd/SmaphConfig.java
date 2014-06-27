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
	private static String configFile;

	public static void setConfigFile(String filename) {
		configFile = filename;
	}

	public static String getDefaultBingKey() {
		if (defaultBingKey == null)
			initialize();
		return defaultBingKey;
	}

	private static void initialize() {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		Document doc;
		try {
			builder = factory.newDocumentBuilder();
			doc = builder.parse(new FileInputStream(configFile));
			defaultBingKey = getConfigValue("bing", "key", doc);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		if (defaultBingKey.isEmpty())
			throw new RuntimeException("Configuration file " + configFile
					+ " has missing value 'key'.");
		if (defaultBingKey.equals("KEY"))
			throw new RuntimeException(
					"Configuration file "
							+ configFile
							+ " has dummy default key value 'KEY'. Please replace with an actual bing key.");
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
