/**
 *  Copyright 2014 Marco Cornolti
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package it.acubelab.smaph;

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
	private static String defaultBingCache;

	/**
	 * Set the configuration file.
	 * 
	 * @param filename
	 *            the configuration file.
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
		if (defaultTagmeKey.isEmpty() || defaultTagmeKey.equals("TAGME_KEY"))
			throw new RuntimeException(
					"Configuration file "
							+ configFile
							+ " has dummy value 'TAGME_KEY' or is unset. Please replace with an actual Tagme key.");

		return defaultTagmeKey;
	}

	/**
	 * @return the default Tagme host.
	 */
	public static String getDefaultTagmeHost() {
		if (defaultTagmeHost == null)
			initialize();
		if (defaultTagmeHost.isEmpty() || defaultTagmeHost.equals("TAGME_HOST"))
			throw new RuntimeException(
					"Configuration file "
							+ configFile
							+ " has dummy value 'TAGME_HOST' or is unset. Please replace with the actual Tagme host.");
		return defaultTagmeHost;
	}

	/**
	 * @return the default Bing key.
	 */
	public static String getDefaultBingKey() {
		if (defaultBingKey == null)
			initialize();
		if (defaultBingKey.isEmpty() || defaultBingKey.equals("BING_KEY"))
			throw new RuntimeException(
					"Configuration file "
							+ configFile
							+ " has dummy value 'BING_KEY' or is unset. Please replace with an actual bing key.");
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
			defaultBingCache = getConfigValue("cache", "bing-cache", doc);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private static String getConfigValue(String setting, String name,
			Document doc) throws XPathExpressionException {
		XPathFactory xPathfactory = XPathFactory.newInstance();
		XPath xpath = xPathfactory.newXPath();
		XPathExpression userExpr = xpath.compile("smaph/setting[@name=\""
				+ setting + "\"]/param[@name=\"" + name + "\"]/@value");
		return userExpr.evaluate(doc);
	}

	public static String getDefaultBingCache() {
		if (defaultBingCache == null)
			initialize();
		return defaultBingCache.isEmpty() ? null : defaultBingCache;
	}

}
