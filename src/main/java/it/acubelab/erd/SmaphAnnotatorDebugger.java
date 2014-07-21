package it.acubelab.erd;

import it.acubelab.batframework.utils.WikipediaApiInterface;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class SmaphAnnotatorDebugger {
	public HashMap<String, List<Triple<String, Integer, HashSet<String>>>> queryToSourceEntityBolds = new HashMap<>();
	public static PrintStream out = System.out;

	public static void activate() {
		out = System.out;
	}

	public static void disable() {
		try {
			out = new PrintStream("/dev/null");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
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
					new MutableTriple<String, Integer, HashSet<String>>(source,
							entity, bolds));
	}

	public JSONObject getBoldsToQuery(WikipediaApiInterface wikiApi) throws JSONException, IOException {
		JSONObject dump = new JSONObject();
		JSONArray mentionEntityDump = new JSONArray();
		dump.put("dump", mentionEntityDump);
		for (String query: queryToSourceEntityBolds.keySet()){
			JSONObject queryData = new JSONObject();
			mentionEntityDump.put(queryData);
			queryData.put("query", query);
			JSONArray boldsEntity = new JSONArray();
			queryData.put("boldsEntity", boldsEntity);
			
			for (Triple<String, Integer, HashSet<String>> data : queryToSourceEntityBolds.get(query)){
				JSONObject entityData = new JSONObject();
				boldsEntity.put(entityData);
				entityData.put("source", data.getLeft());
				entityData.put("wid", data.getMiddle());
				entityData.put("title", wikiApi.getTitlebyId(data.getMiddle()));
				JSONArray bolds = new JSONArray();
				for (String bold: data.getRight())
					bolds.put(bold);
				entityData.put("bolds", bolds);
				entityData.put("url", "http://en.wikipedia.org/wiki/"+URLEncoder.encode(wikiApi.getTitlebyId(data.getMiddle()), "utf8").replace("+", "%20"));
				
			}

		}
		return dump;
	}

}
