package it.acubelab.smaph;

import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.utils.Pair;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.smaph.entityfilters.LibSvmEntityFilter;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class SmaphAnnotatorDebugger {
	public static final PrintStream out = System.out;
	public HashMap<String, List<Triple<String, Integer, HashSet<String>>>> queryToSourceEntityBolds = new HashMap<>();
	private List<String> processedQueries = new Vector<>();
	private HashMap<String, JSONObject> bingResponsesNS = new HashMap<String, JSONObject>();
	private HashMap<String, JSONObject> bingResponsesWS = new HashMap<String, JSONObject>();
	private HashMap<String, List<Triple<String, Integer, Double>>> boldPositionED = new HashMap<>();
	private HashMap<String, List<String>> boldFilterOutput = new HashMap<>();
	private HashMap<String, List<Pair<String, Integer>>> returnedAnnotations = new HashMap<>();
	private HashMap<String, HashMap<Triple<Integer, HashMap<String, Double>, Boolean>, String>> ftrToBoldS1 = new HashMap<>();
	private HashMap<String, List<Triple<Integer, HashMap<String, Double>, Boolean>>> entityFeaturesS1 = new HashMap<>();
	private HashMap<String, List<Triple<Integer, HashMap<String, Double>, Boolean>>> entityFeaturesS2 = new HashMap<>();
	private HashMap<String, List<Triple<Integer, HashMap<String, Double>, Boolean>>> entityFeaturesS3 = new HashMap<>();
	private HashMap<String, List<Triple<Integer, String, Integer>>> source2SearchResult = new HashMap<>();
	private HashMap<String, List<Triple<Integer, String, Integer>>> source3SearchResult = new HashMap<>();
	private HashMap<String, HashSet<Integer>> result = new HashMap<>();
	private HashMap<String, List<Pair<String, Vector<Pair<Integer, Integer>>>>> snippetsToBolds = new HashMap<>();

	public void addProcessedQuery(String query) {
		processedQueries.add(query);
	}

	public void addQueryCandidateBolds(String query, String source, int entity,
			HashSet<String> bolds) {
		if (!queryToSourceEntityBolds.containsKey(query))
			queryToSourceEntityBolds.put(query,
					new Vector<Triple<String, Integer, HashSet<String>>>());
		boolean update = false;
		for (Triple<String, Integer, HashSet<String>> sourceEntityBold : queryToSourceEntityBolds
				.get(query))
			if (sourceEntityBold.getLeft().equals(source)
					&& sourceEntityBold.getMiddle().equals(entity)) {
				sourceEntityBold.getRight().addAll(bolds);
				update = true;
				break;
			}
		if (!update)
			queryToSourceEntityBolds.get(query).add(
					new ImmutableTriple<String, Integer, HashSet<String>>(
							source, entity, bolds));
	}

	private static String widToUrl(int wid, WikipediaApiInterface wikiApi) {
		try {
			return "http://en.wikipedia.org/wiki/"
					+ URLEncoder.encode(wikiApi.getTitlebyId(wid), "utf8")
							.replace("+", "%20");
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public JSONObject getBoldsToQuery(WikipediaApiInterface wikiApi)
			throws JSONException, IOException {
		JSONObject dump = new JSONObject();
		JSONArray mentionEntityDump = new JSONArray();
		dump.put("dump", mentionEntityDump);
		for (String query : queryToSourceEntityBolds.keySet()) {
			JSONObject queryData = new JSONObject();
			mentionEntityDump.put(queryData);
			queryData.put("query", query);
			JSONArray boldsEntity = new JSONArray();
			queryData.put("boldsEntity", boldsEntity);

			for (Triple<String, Integer, HashSet<String>> data : queryToSourceEntityBolds
					.get(query)) {
				JSONObject entityData = new JSONObject();
				boldsEntity.put(entityData);
				entityData.put("source", data.getLeft());
				entityData.put("wid", data.getMiddle());
				entityData.put("title", wikiApi.getTitlebyId(data.getMiddle()));
				JSONArray bolds = new JSONArray();
				for (String bold : data.getRight())
					bolds.put(bold);
				entityData.put("bolds", bolds);
				entityData.put("url", widToUrl(data.getMiddle(), wikiApi));

			}

		}
		return dump;
	}

	public void addBingResponseNormalSearch(String query,
			JSONObject bingResponse) {
		this.bingResponsesNS.put(query, bingResponse);
	}

	public JSONObject getBingResponseNormalSearch(String query) {
		return this.bingResponsesNS.get(query);
	}

	public void addBingResponseWikiSearch(String query, JSONObject bingResponse) {
		this.bingResponsesWS.put(query, bingResponse);
	}

	public JSONObject getBingResponseWikiSearch(String query) {
		return this.bingResponsesWS.get(query);
	}

	public void addBoldPositionEditDistance(String query,
			List<Pair<String, Integer>> bingBoldsAndRanks) {
		if (!this.boldPositionED.containsKey(query))
			this.boldPositionED.put(query,
					new Vector<Triple<String, Integer, Double>>());
		for (Pair<String, Integer> bingBoldsAndRank : bingBoldsAndRanks)
			this.boldPositionED.get(query).add(
					new ImmutableTriple<>(bingBoldsAndRank.first,
							bingBoldsAndRank.second, SmaphUtils.getMinEditDist(
									query, bingBoldsAndRank.first)));
	}

	public JSONArray getBoldPositionEditDistance(String query)
			throws JSONException {
		JSONArray res = new JSONArray();
		for (Triple<String, Integer, Double> triple : this.boldPositionED
				.get(query)) {
			JSONObject tripleJs = new JSONObject();
			res.put(tripleJs);
			tripleJs.put("bold", triple.getLeft());
			tripleJs.put("rank", triple.getMiddle());
			tripleJs.put("editDistance", triple.getRight());
		}
		return res;
	}

	public void addSnippets(String query,
			List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBold) {
		this.snippetsToBolds.put(query, snippetsToBold);
	}

	public JSONArray getSnippets(String query) throws JSONException {
		JSONArray res = new JSONArray();
		List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBolds = this.snippetsToBolds
				.get(query);
		for (Pair<String, Vector<Pair<Integer, Integer>>> snippetsToBold : snippetsToBolds) {
			JSONObject objI = new JSONObject();
			res.put(objI);
			objI.put("snippet", snippetsToBold.first);
			JSONArray positionsI = new JSONArray();
			objI.put("bold_positions", positionsI);
			for (Pair<Integer, Integer> startAndLength : snippetsToBold.second) {
				JSONObject position = new JSONObject();
				positionsI.put(position);
				position.put("start", startAndLength.first);
				position.put("length", startAndLength.second);
			}
		}
		return res;
	}

	public void addBoldFilterOutput(String query, List<String> bolds) {
		this.boldFilterOutput.put(query, bolds);
	}

	public JSONArray getBoldFilterOutput(String query) throws JSONException {
		JSONArray res = new JSONArray();
		for (String bold : this.boldFilterOutput.get(query))
			res.put(bold);
		return res;
	}

	public void addReturnedAnnotation(String query,
			HashMap<String, Annotation> spotToAnnotation) {
		if (!this.returnedAnnotations.containsKey(query))
			this.returnedAnnotations.put(query,
					new Vector<Pair<String, Integer>>());
		for (String bold : spotToAnnotation.keySet())
			this.returnedAnnotations.get(query).add(
					new Pair<>(bold, spotToAnnotation.get(bold).getConcept()));
	}

	public JSONArray getReturnedAnnotations(String query,
			WikipediaApiInterface wikiApi) throws JSONException, IOException {
		JSONArray res = new JSONArray();
		for (Pair<String, Integer> p : this.returnedAnnotations.get(query)) {
			JSONObject pairJs = new JSONObject();
			res.put(pairJs);
			pairJs.put("bold", p.first);
			pairJs.put("wid", p.second);
			pairJs.put("title", wikiApi.getTitlebyId(p.second));
			pairJs.put("url", widToUrl(p.second, wikiApi));
		}
		return res;
	}

	public void addEntityFeaturesS1(String query, String bold, int wid,
			HashMap<String, Double> features, boolean accepted) {
		ImmutableTriple<Integer, HashMap<String, Double>, Boolean> ftrTriple = addEntityFeatures(
				this.entityFeaturesS1, query, wid, features, accepted);

		if (!ftrToBoldS1.containsKey(query))
			ftrToBoldS1
					.put(query,
							new HashMap<Triple<Integer, HashMap<String, Double>, Boolean>, String>());
		ftrToBoldS1.get(query).put(ftrTriple, bold);
	}

	public void addEntityFeaturesS2(String query, int wid,
			HashMap<String, Double> features, boolean accepted) {
		addEntityFeatures(this.entityFeaturesS2, query, wid, features, accepted);
	}

	public void addEntityFeaturesS3(String query, int wid,
			HashMap<String, Double> features, boolean accepted) {
		addEntityFeatures(this.entityFeaturesS3, query, wid, features, accepted);
	}

	private ImmutableTriple<Integer, HashMap<String, Double>, Boolean> addEntityFeatures(
			HashMap<String, List<Triple<Integer, HashMap<String, Double>, Boolean>>> source,
			String query, int wid, HashMap<String, Double> features,
			boolean accepted) {
		if (!source.containsKey(query))
			source.put(
					query,
					new Vector<Triple<Integer, HashMap<String, Double>, Boolean>>());
		ImmutableTriple<Integer, HashMap<String, Double>, Boolean> ftrTriple = new ImmutableTriple<>(
				wid, features, accepted);
		source.get(query).add(ftrTriple);
		return ftrTriple;
	}

	private JSONArray getEntityFeatures(
			HashMap<String, List<Triple<Integer, HashMap<String, Double>, Boolean>>> source,
			String query, WikipediaApiInterface wikiApi) throws JSONException,
			IOException {
		JSONArray res = new JSONArray();
		if (source.containsKey(query))
			for (Triple<Integer, HashMap<String, Double>, Boolean> p : source
					.get(query)) {
				JSONObject pairJs = new JSONObject();
				res.put(pairJs);
				String bold = ftrToBoldS1.get(query).get(p);
				if (bold != null)
					pairJs.put("bold", bold);
				pairJs.put("wid", p.getLeft());
				pairJs.put("title", wikiApi.getTitlebyId(p.getLeft()));
				pairJs.put("url", widToUrl(p.getLeft(), wikiApi));
				JSONObject features = new JSONObject();
				pairJs.put("features", features);
				for (String ftrName : LibSvmEntityFilter.ftrNames)
					features.put(ftrName, p.getMiddle().get(ftrName));
				pairJs.put("accepted", p.getRight());
			}
		return res;
	}

	private void addSourceSearchResult(
			HashMap<String, List<Triple<Integer, String, Integer>>> source,
			String query, HashMap<Integer, Integer> rankToIdNS,
			List<String> urls) {
		if (!source.containsKey(query))
			source.put(query, new Vector<Triple<Integer, String, Integer>>());
		for (int i = 0; i < urls.size(); i++)
			source.get(query).add(
					new ImmutableTriple<>(i, urls.get(i), rankToIdNS
							.containsKey(i) ? rankToIdNS.get(i) : -1));
	}

	public void addSource2SearchResult(String query,
			HashMap<Integer, Integer> rankToIdNS, List<String> urls) {
		addSourceSearchResult(source2SearchResult, query, rankToIdNS, urls);
	}

	public void addSource3SearchResult(String query,
			HashMap<Integer, Integer> rankToIdWS, List<String> urls) {
		addSourceSearchResult(source3SearchResult, query, rankToIdWS, urls);
	}

	public JSONArray getSourceSearchResult(
			HashMap<String, List<Triple<Integer, String, Integer>>> source,
			String query, WikipediaApiInterface wikiApi) throws JSONException,
			IOException {
		JSONArray res = new JSONArray();
		for (Triple<Integer, String, Integer> t : source.get(query)) {
			JSONObject triple = new JSONObject();
			res.put(triple);
			triple.put("rank", t.getLeft());
			triple.put("wid", t.getRight());
			triple.put("title",
					t.getRight() >= 0 ? wikiApi.getTitlebyId(t.getRight())
							: "---not a wikipedia page---");
			triple.put("url", t.getMiddle());
		}
		return res;
	}

	public void addResult(String query, int wid) {
		if (!this.result.containsKey(query))
			this.result.put(query, new HashSet<Integer>());
		this.result.get(query).add(wid);

	}

	private JSONArray getResults(String query, WikipediaApiInterface wikiApi)
			throws JSONException, IOException {
		JSONArray res = new JSONArray();
		if (result.containsKey(query))
			for (Integer wid : result.get(query)) {
				JSONObject triple = new JSONObject();
				res.put(triple);
				triple.put("wid", wid);
				triple.put("title", wikiApi.getTitlebyId(wid));
				triple.put("url", widToUrl(wid, wikiApi));
			}
		return res;
	}

	public JSONObject toJson(WikipediaApiInterface wikiApi)
			throws JSONException, IOException {
		JSONObject dump = new JSONObject();

		for (String query : processedQueries) {
			JSONObject queryData = new JSONObject();
			dump.put(query, queryData);
			JSONObject phase1 = new JSONObject();
			JSONObject phase1S1 = new JSONObject();
			JSONObject phase1S2 = new JSONObject();
			JSONObject phase1S3 = new JSONObject();
			queryData.put("bingResponseNS", getBingResponseNormalSearch(query));
			queryData.put("bingResponseWS", getBingResponseWikiSearch(query));
			queryData.put("phase1", phase1);
			phase1.put("source1", phase1S1);
			phase1.put("source2", phase1S2);
			phase1.put("source3", phase1S3);

			/** Populate phase1 - source1 */
			phase1S1.put("bolds", getBoldPositionEditDistance(query));
			phase1S1.put("snippets", getSnippets(query));
			phase1S1.put("filteredBolds", getBoldFilterOutput(query));
			phase1S1.put("annotations", getReturnedAnnotations(query, wikiApi));
			phase1S1.put("entityFeatures",
					getEntityFeatures(this.entityFeaturesS1, query, wikiApi));

			/** Populate phase1 - source2 */
			phase1S2.put("pages",
					getSourceSearchResult(source2SearchResult, query, wikiApi));
			phase1S2.put("entityFeatures",
					getEntityFeatures(this.entityFeaturesS2, query, wikiApi));

			/** Populate phase1 - source3 */
			phase1S3.put("pages",
					getSourceSearchResult(source3SearchResult, query, wikiApi));
			phase1S3.put("entityFeatures",
					getEntityFeatures(this.entityFeaturesS3, query, wikiApi));

			/** Populate results */
			queryData.put("results", getResults(query, wikiApi));
		}
		return dump;
	}

}
