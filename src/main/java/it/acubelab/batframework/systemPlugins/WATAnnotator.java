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

package it.acubelab.batframework.systemPlugins;

import java.io.*;
import java.net.*;
import java.util.*;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.data.Mention;
import it.acubelab.batframework.data.MultipleAnnotation;
import it.acubelab.batframework.data.ScoredAnnotation;
import it.acubelab.batframework.data.ScoredTag;
import it.acubelab.batframework.data.Tag;
import it.acubelab.batframework.problems.CandidatesSpotter;
import it.acubelab.batframework.problems.MentionSpotter;
import it.acubelab.batframework.problems.Sa2WSystem;
import it.acubelab.batframework.utils.AnnotationException;
import it.acubelab.batframework.utils.ProblemReduction;
import it.acubelab.smaph.SmaphAnnotatorDebugger;

public class WATAnnotator implements Sa2WSystem, MentionSpotter,
		CandidatesSpotter {
	private static final int RETRY_N = 2;
	private long lastTime = 0;
	private boolean useContext, useTagger, bogusFilter;
	private final String urlTag;
	private final String urlSpot;
	private final String urlD2W;
	private final String method, relatedness, windowSize, minCommonness,
			minLinkProbability, epsilon, kappa;
	private String sortBy;
	private HashMap<String, HashMap<String, Double>> additionalInfo = new HashMap<>();
	private HashMap<String, List<HashMap<String, Double>>> additionalCandidatesInfo = new HashMap<>();
	private boolean brutalD2WReduction = false;
	private static HashMap<String, JSONObject> url2jsonCache = null;
	private static long flushCounter = 0;
	private static final int FLUSH_EVERY = 200;
	private static String resultsCacheFilename = null;

	public static synchronized void increaseFlushCounter()
			throws FileNotFoundException, IOException {
		flushCounter++;
		if ((flushCounter % FLUSH_EVERY) == 0)
			flush();
	}

	public static synchronized void flush() throws FileNotFoundException,
			IOException {
		if (flushCounter > 0 && resultsCacheFilename != null) {
			SmaphAnnotatorDebugger.out.print("Flushing WikiSense cache... ");
			new File(resultsCacheFilename).createNewFile();
			ObjectOutputStream oos = new ObjectOutputStream(
					new FileOutputStream(resultsCacheFilename));
			oos.writeObject(url2jsonCache);
			oos.close();
			SmaphAnnotatorDebugger.out
					.println("Flushing WikiSense cache Done.");
		}
	}

	public static void setCache(String cacheFilename)
			throws FileNotFoundException, IOException, ClassNotFoundException {
		System.out.println("Loading wikisense cache...");
		resultsCacheFilename = cacheFilename;
		if (new File(resultsCacheFilename).exists()) {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
					resultsCacheFilename));
			url2jsonCache = (HashMap<String, JSONObject>) ois.readObject();
			ois.close();
		}
	}

	public static void unSetCache() {
		url2jsonCache = null;
		System.gc();
	}

	public WATAnnotator(String ip, int port, String method) {
		this(ip, port, method, "PAGERANK", "mw", "", "");
	}

	public WATAnnotator(String ip, int port, String method, String sortBy,
			String relatedness, String epsilon, String minLinkProbability) {
		this(ip, port, method, sortBy, relatedness, epsilon,
				minLinkProbability, false, false, false);
	}

	public WATAnnotator(String ip, int port, String method, String sortBy,
			String relatedness, String epsilon, String minLinkProbability,
			boolean useContext, boolean useTagger, boolean bogusFilter) {
		this.urlTag = String.format("http://%s:%d/tag/tag", ip, port);
		this.urlSpot = String.format("http://%s:%d/tag/spot", ip, port);
		this.urlD2W = String.format("http://%s:%d/tag/disambiguate", ip, port);
		this.method = method;
		this.epsilon = epsilon;
		this.windowSize = "";
		this.minCommonness = "";
		this.kappa = "";
		this.useContext = useContext;
		this.useTagger = useTagger;
		this.bogusFilter = bogusFilter;
		this.minLinkProbability = minLinkProbability;
		this.sortBy = sortBy;
		this.relatedness = relatedness;
	}

	@Override
	public HashSet<Annotation> solveA2W(String text) throws AnnotationException {
		return null;
	}

	@Override
	public HashSet<Tag> solveC2W(String text) throws AnnotationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getName() {
		return String
				.format("WikiSense (method=%s epsilon=%s usecontext=%b relatedness=%s sortby=%s)",
						method, epsilon.equals("") ? "default" : epsilon,
						useContext, relatedness, sortBy);
	}

	@Override
	public long getLastAnnotationTime() {
		return lastTime;
	}

	public HashSet<Annotation> solveD2WParams(String text,
			HashSet<Mention> mentions, String newMinCommonness,
			String newEpsilon, String kappa) throws JSONException {
		System.out.println(text.substring(0, Math.min(30, text.length())));
		HashSet<Annotation> res = new HashSet<Annotation>();
		JSONObject obj = null;

		try {
			obj = queryJson(text, mentions, urlD2W,
					generateGetParameters(newMinCommonness, newEpsilon, kappa),
					RETRY_N);
			System.out.println(obj);
			System.out.println(((JSONObject) obj.get("time")).get("total"));
			lastTime = (Long) ((JSONObject) obj.get("time")).get("total");

		} catch (Exception e) {
			System.err
					.print("Got error while querying WikiSense API with GET parameters: "
							+ generateGetParameters(newMinCommonness,
									newEpsilon, kappa) + " with text: " + text);
			throw new AnnotationException(
					"An error occurred while querying WikiSense API. Message: "
							+ e.getMessage());
		}
		JSONArray jsAnnotations = (JSONArray) obj.get("annotations");
		for (int i = 0; i < jsAnnotations.length(); i++) {
			JSONObject js_ann = (JSONObject) jsAnnotations.get(i);
			// System.out.println(js_ann);
			int start = ((Long) js_ann.get("start")).intValue();
			int end = ((Long) js_ann.get("end")).intValue();
			int id = ((Long) js_ann.get("id")).intValue();
			double lp = ((Double) js_ann.get("linkProb")).doubleValue();
			double commonness = ((Double) js_ann.get("commonness"))
					.doubleValue();
			double rhoScore = ((Double) js_ann.get("rho")).doubleValue();
			double ambiguity = 1.0 / (1.0 + ((Long) js_ann.get("ambiguity"))
					.intValue());
			double localCoherence = ((Double) js_ann.get("localCoherence"))
					.doubleValue();
			double pageRank = ((Double) js_ann.get("pageRank")).doubleValue();
			// System.out.println(text.substring(start, end) + "->" + id);

			Mention m = new Mention(start, end - start);
			if (mentions.contains(m))
				res.add(new Annotation(m.getPosition(), m.getLength(), id));

			String mention = text.substring(start, end);
			if (!additionalInfo.containsKey(mention))
				additionalInfo.put(mention, new HashMap<String, Double>());
			additionalInfo.get(mention).put("lp", lp);
			additionalInfo.get(mention).put("commonness", commonness);
			additionalInfo.get(mention).put("rhoScore", rhoScore);
			additionalInfo.get(mention).put("ambiguity", ambiguity);
			additionalInfo.get(mention).put("localCoherence", localCoherence);
			additionalInfo.get(mention).put("pageRank", pageRank);

			JSONArray jsRankings = (JSONArray) js_ann.get("ranking");
			int rank = 0;
			for (int j = 0; j < jsRankings.length(); j++) {
				JSONObject jsRanking = (JSONObject) jsRankings.get(j);
				id = ((Long) jsRanking.get("id")).intValue();
				commonness = ((Double) jsRanking.get("commonness"))
						.doubleValue();
				double score = ((Double) jsRanking.get("score")).doubleValue();
				pageRank = ((Double) jsRanking.get("pageRank")).doubleValue();
				int synonimy = ((Long) jsRanking.get("synonymy")).intValue();

				HashMap<String, Double> values = new HashMap<>();
				values.put("id", (double) id);
				values.put("rank", (double) rank);
				values.put("commonness", (double) commonness);
				values.put("score", (double) score);
				values.put("pageRank", (double) pageRank);
				values.put("synonimy", (double) synonimy);
				values.put("lp", (double) lp);
				values.put("ambiguity", (double) ambiguity);
				if (!additionalCandidatesInfo.containsKey(mention))
					additionalCandidatesInfo.put(mention,
							new Vector<HashMap<String, Double>>());
				additionalCandidatesInfo.get(mention).add(values);
				rank++;
			}
		}
		return res;
	}

	@Override
	public HashSet<Annotation> solveD2W(String text, HashSet<Mention> mentions)
			throws AnnotationException {
		if (brutalD2WReduction)
			return ProblemReduction.Sa2WToD2W(this.solveSa2W(text), mentions,
					-1f);
		try {
			return solveD2WParams(text, mentions, minCommonness, epsilon, kappa);
		} catch (JSONException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	@Override
	public HashSet<ScoredTag> solveSc2W(String text) throws AnnotationException {
		// System.out.println(text);
		HashSet<ScoredTag> res = new HashSet<ScoredTag>();
		JSONObject obj = null;
		String getParameters = String.format("lang=%s", "en");
		if (!method.equals(""))
			getParameters += String.format("&method=%s", method);
		if (!windowSize.equals(""))
			getParameters += String.format("&windowSize=%s", windowSize);
		if (!epsilon.equals(""))
			getParameters += String.format("&epsilon=%s", epsilon);
		if (!minCommonness.equals(""))
			getParameters += String.format("&minCommonness=%s", minCommonness);
		try {
			obj = queryJson(text, null, urlTag, getParameters, RETRY_N);
			lastTime = (Long) ((JSONObject) obj.get("time")).get("total");

		} catch (Exception e) {
			System.out
					.print("Got error while querying WikiSense API with GET parameters: "
							+ getParameters + " with text: " + text);
			throw new AnnotationException(
					"An error occurred while querying WikiSense API. Message: "
							+ e.getMessage());
		}

		try {
			JSONArray jsAnnotations = (JSONArray) obj.get("annotations");
			for (int i = 0; i < jsAnnotations.length(); i++) {
				JSONObject js_ann = (JSONObject) jsAnnotations.get(i);
				JSONArray jsRanking = (JSONArray) js_ann.get("ranking");
				// System.out.println(jsRanking);
				for (int j = 0; j < jsRanking.length(); j++) {
					JSONObject jsCand = (JSONObject) jsRanking.get(j);
					int id = ((Long) jsCand.get("id")).intValue();
					double rho = (Double) jsCand.get("score");
					// System.out.println(id + " (" + rho + ")");
					res.add(new ScoredTag(id, (float) rho));
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
			throw new AnnotationException(e.getMessage());
		}

		return res;
	}

	@Override
	public HashSet<ScoredAnnotation> solveSa2W(String text)
			throws AnnotationException {
		// System.out.println(text);
		HashSet<ScoredAnnotation> res = new HashSet<ScoredAnnotation>();
		JSONObject obj = null;
		try {
			obj = queryJson(text, null, urlTag,
					generateGetParameters(minCommonness, epsilon, kappa),
					RETRY_N);
			lastTime = (Long) ((JSONObject) obj.get("time")).get("total");

		} catch (Exception e) {
			System.out
					.print("Got error while querying WikiSense API with GET parameters: "
							+ generateGetParameters(minCommonness, epsilon,
									kappa) + " with text: " + text);

			throw new AnnotationException(
					"An error occurred while querying WikiSense API. Message: "
							+ e.getMessage());
		}
		try {
			JSONArray jsAnnotations = (JSONArray) obj.get("annotations");
			for (int i = 0; i < jsAnnotations.length(); i++) {
				JSONObject js_ann = (JSONObject) jsAnnotations.get(i);
				// System.out.println(js_ann);
				int start = ((Long) js_ann.get("start")).intValue();
				int end = ((Long) js_ann.get("end")).intValue();
				int id = ((Long) js_ann.get("id")).intValue();
				double rho = (Double) js_ann.get("rho");
				// System.out.println(text.substring(start, end) + "->" + id +
				// " ("
				// + rho + ")");
				res.add(new ScoredAnnotation(start, end - start, id,
						(float) rho));
			}
		} catch (JSONException e) {
			e.printStackTrace();
			throw new AnnotationException(e.getMessage());
		}
		return res;
	}

	@Override
	public HashSet<Mention> getSpottedMentions(String text) {
		HashSet<Mention> res = new HashSet<Mention>();
		JSONObject obj = null;
		String getParameters = String.format("lang=%s", "en", method);
		try {
			obj = queryJson(text, null, urlSpot, getParameters, RETRY_N);
			// System.out.println(obj);
		} catch (Exception e) {
			System.out
					.print("Got error while querying WikiSense API with GET parameters: "
							+ getParameters + " with text: " + text);
			throw new AnnotationException(
					"An error occurred while querying WikiSense API. Message: "
							+ e.getMessage());
		}
		try {
			JSONArray jsSpots = (JSONArray) obj.get("spots");
			for (int i = 0; i < jsSpots.length(); i++) {
				JSONObject jsSpot = (JSONObject) jsSpots.get(i);
				// System.out.println(jsSpot);
				int start = ((Long) jsSpot.get("start")).intValue();
				int end = ((Long) jsSpot.get("end")).intValue();
				// System.out.printf("Found spot: [%s]%n", text.substring(start,
				// end));
				Mention newMention = new Mention(start, end - start);
				res.add(newMention);
			}
		} catch (JSONException e) {
			e.printStackTrace();
			throw new AnnotationException(e.getMessage());
		}
		return res;
	}

	private String generateGetParameters(String newMinCommonness,
			String newEpsilon, String newKappa) {
		String getParameters = String.format("lang=%s", "en");
		if (!method.equals(""))
			getParameters += String.format("&method=%s", method);
		if (!windowSize.equals(""))
			getParameters += String.format("&windowSize=%s", windowSize);
		if (!newEpsilon.equals(""))
			getParameters += String.format("&epsilon=%s", newEpsilon);
		if (!newMinCommonness.equals(""))
			getParameters += String.format("&minCommonness=%s",
					newMinCommonness);
		if (!newKappa.equals(""))
			getParameters += String.format("&kappa=%s", newKappa);
		if (!minLinkProbability.equals(""))
			getParameters += String.format("&minLinkProbability=%s",
					minLinkProbability);
		if (!relatedness.equals(""))
			getParameters += String.format("&relatedness=%s", relatedness);
		if (!sortBy.equals(""))
			getParameters += String.format("&sortBy=%s", sortBy);
		getParameters += "&bogusFilter=" + this.bogusFilter;
		getParameters += "&useTagger=" + this.useTagger;
		getParameters += "&useContext=" + this.useContext;
		return getParameters;
	}

	private JSONObject queryJson(String text, Set<Mention> mentions,
			String url, String getParameters, int retry) throws Exception {

		JSONObject parameters = new JSONObject();
		if (mentions != null) {
			JSONArray mentionsJson = new JSONArray();
			for (Mention m : mentions) {
				JSONObject mentionJson = new JSONObject();
				mentionJson.put("start", m.getPosition());
				mentionJson.put("end", m.getPosition() + m.getLength());
				mentionsJson.put(mentionJson);
			}
			parameters.put("spans", mentionsJson);
		}
		parameters.put("text", text);
		System.out.println(getParameters);
		System.out.println(parameters.toString());

		String resultStr = null;
		try {
			URL wikiSenseApi = new URL(String.format("%s?%s", url,
					getParameters));

			String cacheKey = wikiSenseApi.toExternalForm()
					+ parameters.toString();
			if (url2jsonCache != null && url2jsonCache.containsKey(cacheKey))
				return url2jsonCache.get(cacheKey);

			HttpURLConnection slConnection = (HttpURLConnection) wikiSenseApi
					.openConnection();
			slConnection.setReadTimeout(0);
			slConnection.setDoOutput(true);
			slConnection.setDoInput(true);
			slConnection.setRequestMethod("POST");
			slConnection.setRequestProperty("Content-Type", "application/json");
			slConnection.setRequestProperty("Content-Length", ""
					+ parameters.toString().getBytes().length);

			slConnection.setUseCaches(false);

			DataOutputStream wr = new DataOutputStream(
					slConnection.getOutputStream());
			wr.write(parameters.toString().getBytes());
			wr.flush();
			wr.close();

			if (slConnection.getResponseCode() != 200) {
				Scanner s = new Scanner(slConnection.getErrorStream())
						.useDelimiter("\\A");
				System.err.printf("Got HTTP error %d. Message is: %s%n",
						slConnection.getResponseCode(), s.next());
				s.close();
			}

			Scanner s = new Scanner(slConnection.getInputStream())
					.useDelimiter("\\A");
			resultStr = s.hasNext() ? s.next() : "";

			JSONObject obj = new JSONObject(resultStr);
			if (url2jsonCache != null)
				url2jsonCache.put(cacheKey, obj);
			increaseFlushCounter();

			return obj;

		} catch (Exception e) {
			try {
				Thread.sleep(3000);
				if (retry > 0)
					return queryJson(text, mentions, url, getParameters,
							retry - 1);
				else
					throw e;
			} catch (InterruptedException e1) {
				e1.printStackTrace();
				throw new RuntimeException(e1);
			}
		}
	}

	@Override
	public HashSet<MultipleAnnotation> getSpottedCandidates(String text) {
		HashSet<MultipleAnnotation> res = new HashSet<MultipleAnnotation>();
		JSONObject obj = null;
		String getParameters = String.format(
				"lang=%s&includeEntities=true&sortBy=SCORE", "en");
		try {
			obj = queryJson(text, null, urlSpot, getParameters, RETRY_N);
		} catch (Exception e) {
			System.out
					.print("Got error while querying WikiSense API with GET parameters: "
							+ getParameters + " with text: " + text);
			throw new AnnotationException(
					"An error occurred while querying WikiSense API. Message: "
							+ e.getMessage());
		}
		try {
			JSONArray jsSpots = (JSONArray) obj.get("spots");
			for (int i = 0; i < jsSpots.length(); i++) {
				JSONObject jsSpot = (JSONObject) jsSpots.get(i);
				int start = ((Long) jsSpot.get("start")).intValue();
				int end = ((Long) jsSpot.get("end")).intValue();

				JSONArray jsRanking = (JSONArray) jsSpot.get("ranking");
				int[] rankedCandidates = new int[jsRanking.length()];
				for (int j = 0; j < jsRanking.length(); j++) {
					JSONObject jsCand = (JSONObject) jsRanking.get(j);
					int id = ((Long) jsCand.get("id")).intValue();
					rankedCandidates[j] = id;
				}
				MultipleAnnotation newAnnotation = new MultipleAnnotation(
						start, end - start, rankedCandidates);
				res.add(newAnnotation);
			}
		} catch (JSONException e) {
			e.printStackTrace();
			throw new AnnotationException(e.getMessage());
		}
		return res;
	}

	public HashMap<String, HashMap<String, Double>> getLastQueryAdditionalInfo() {
		HashMap<String, HashMap<String, Double>> clone = new HashMap<>(
				additionalInfo);
		additionalInfo.clear();
		return clone;
	}

	public HashMap<String, List<HashMap<String, Double>>> getLastQueryAdditionalCandidatesInfo() {
		HashMap<String, List<HashMap<String, Double>>> clone = new HashMap<>(
				additionalCandidatesInfo);
		additionalCandidatesInfo.clear();
		return clone;
	}

	public void setBrutalD2WReduction() {
		this.brutalD2WReduction = true;
	}
}
