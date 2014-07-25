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

import it.acubelab.batframework.data.*;
import it.acubelab.batframework.problems.Sa2WSystem;
import it.acubelab.batframework.systemPlugins.WATAnnotator;
import it.acubelab.batframework.utils.*;
import it.acubelab.smaph.boldfilters.*;
import it.acubelab.smaph.entityfilters.*;
import it.acubelab.smaph.main.ERDDatasetFilter;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.codehaus.jettison.json.*;
import org.xml.sax.SAXException;

import com.sun.org.apache.xml.internal.security.utils.Base64;

public class SmaphAnnotator implements Sa2WSystem {
	private static final String WIKI_URL_LEADING = "http://en.wikipedia.org/wiki/";
	private static final int BING_RETRY = 3;
	private String bingKey;
	private static final int FLUSH_EVERY = 50;
	public static final String WIKITITLE_ENDPAR_REGEX = "\\s*\\([^\\)]*\\)\\s*$";
	private static HashMap<String, JSONObject> url2jsonCache = new HashMap<>();
	private static String resultsCacheFilename;
	private static int flushCounter = 0;
	private WikipediaApiInterface wikiApi;

	private WATAnnotator auxDisambiguator;
	private BoldFilter boldFilter;
	private EntityFilter entityFilter;
	private boolean includeSourceNormalSearch;
	private boolean includeSourceAnnotator;
	private boolean includeSourceWikiSearch;
	private int topKWikiSearch = 0;
	private boolean includeSourceRelatedSearch;
	private int topKRelatedSearch;
	private SmaphAnnotatorDebugger debugger;

	/**
	 * Constructs a SMAPH annotator.
	 * 
	 * @param auxDisambiguator
	 *            the disambiguator used in Source 1.
	 * @param boldFilter
	 *            the filter of the bolds used in Source 1.
	 * @param entityFilter
	 *            the entity filter used in the second stage.
	 * @param includeSourceAnnotator
	 *            true iff Source 1 has to be enabled.
	 * @param includeSourceNormalSearch
	 *            true iff Source 2 has to be enabled.
	 * @param includeSourceWikiSearch
	 *            true iff Source 3 has to be enabled.
	 * @param wikiSearchPages
	 *            Source 3 results limit.
	 * @param includeRelatedSearch
	 *            true iff Source 4 has to be enabled.
	 * @param topKRelatedSearch
	 *            Source 4 results limit.
	 * @param wikiApi
	 *            an API to Wikipedia.
	 * @param bingKey
	 *            the key to the Bing API.
	 */
	public SmaphAnnotator(WATAnnotator auxDisambiguator,
			BoldFilter boldFilter, EntityFilter entityFilter,
			boolean includeSourceAnnotator, boolean includeSourceNormalSearch,
			boolean includeSourceWikiSearch, int wikiSearchPages,
			boolean includeSourceAnnotatorTopK, int topKAnnotatorCandidates,
			boolean includeRelatedSearch, int topKRelatedSearch,
			WikipediaApiInterface wikiApi, String bingKey) {
		this.auxDisambiguator = auxDisambiguator;
		this.boldFilter = boldFilter;
		this.entityFilter = entityFilter;
		this.wikiApi = wikiApi;
		this.includeSourceAnnotator = includeSourceAnnotator;
		this.includeSourceNormalSearch = includeSourceNormalSearch;
		this.includeSourceWikiSearch = includeSourceWikiSearch;
		this.topKWikiSearch = wikiSearchPages;
		this.includeSourceRelatedSearch = includeRelatedSearch;
		this.topKRelatedSearch = topKRelatedSearch;
		this.bingKey = bingKey;
	}

	/**
	 * Set an optional debugger to gather data about the process of a query.
	 * 
	 * @param debugger
	 *            the debugger.
	 */
	public void setDebugger(SmaphAnnotatorDebugger debugger) {
		this.debugger = debugger;
	}

	private static synchronized void increaseFlushCounter()
			throws FileNotFoundException, IOException {
		flushCounter++;
		if (flushCounter % FLUSH_EVERY == 0)
			flush();
	}

	/**
	 * Flushes the cache of the Bing api.
	 * 
	 * @throws FileNotFoundException
	 *             if the file exists but is a directory rather than a regular
	 *             file, does not exist but cannot be created, or cannot be
	 *             opened for any other reason.
	 * @throws IOException
	 *             if an I/O error occurred.
	 */
	public static synchronized void flush() throws FileNotFoundException,
			IOException {
		if (flushCounter > 0 && resultsCacheFilename != null) {
			SmaphAnnotatorDebugger.out.print("Flushing Bing cache... ");
			new File(resultsCacheFilename).createNewFile();
			ObjectOutputStream oos = new ObjectOutputStream(
					new FileOutputStream(resultsCacheFilename));
			oos.writeObject(url2jsonCache);
			oos.close();
			SmaphAnnotatorDebugger.out.println("Flushing Bing cache Done.");
		}
	}

	@Override
	public HashSet<Annotation> solveA2W(String text) throws AnnotationException {
		return null;
	}

	@Override
	public HashSet<Tag> solveC2W(String text) throws AnnotationException {
		return ProblemReduction.A2WToC2W(ProblemReduction.Sa2WToA2W(this
				.solveSa2W(text)));
	}

	@Override
	public String getName() {
		return "Smaph annotator";
	}

	@Override
	public long getLastAnnotationTime() {
		return 0;
	}

	@Override
	public HashSet<Annotation> solveD2W(String text, HashSet<Mention> mentions)
			throws AnnotationException {
		return null;
	}

	@Override
	public HashSet<ScoredTag> solveSc2W(String text) throws AnnotationException {
		return null;
	}

	/**
	 * Call the disambiguator and disambiguate the bolds.
	 * 
	 * @param text
	 *            concatenated bolds.
	 * @param mentions
	 *            mentions (one per bold).
	 * @return a triple that has: additional info returned by the annotator for
	 *         the query as left element; the mapping from bold to annotation as
	 *         middle element; additional candidates info as right element.
	 * @throws IOException
	 * @throws XPathExpressionException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 */
	private Pair<HashMap<String, HashMap<String, Double>>, HashMap<String, Annotation>> disambiguateBolds(
			String text, HashSet<Mention> mentions) throws IOException,
			XPathExpressionException, ParserConfigurationException,
			SAXException {
		HashSet<Annotation> anns;
		anns = auxDisambiguator.solveD2W(text, mentions);
		if (anns == null)
			return new Pair<HashMap<String, HashMap<String, Double>>, HashMap<String, Annotation>>(
					new HashMap<String, HashMap<String, Double>>(),
					new HashMap<String, Annotation>());

		List<Integer> widsToPrefetch = new Vector<>();
		for (Annotation ann : anns)
			widsToPrefetch.add(ann.getConcept());
		wikiApi.prefetchWids(widsToPrefetch);

		HashMap<String, List<HashMap<String, Double>>> additionalCandidatesInfo = auxDisambiguator
				.getLastQueryAdditionalCandidatesInfo();
		for (String mention : additionalCandidatesInfo.keySet())
			additionalCandidatesInfo.put(mention,
					additionalCandidatesInfo.get(mention));

		HashMap<String, Annotation> spotToAnnotation = new HashMap<>();
		for (Annotation ann : anns)
			spotToAnnotation.put(
					text.substring(ann.getPosition(),
							ann.getPosition() + ann.getLength()), ann);
		return new Pair<HashMap<String, HashMap<String, Double>>, HashMap<String, Annotation>>(
				auxDisambiguator.getLastQueryAdditionalInfo(), spotToAnnotation);
	}

	@Override
	public HashSet<ScoredAnnotation> solveSa2W(String query)
			throws AnnotationException {
		if (debugger != null)
			debugger.addProcessedQuery(query);

		HashSet<ScoredAnnotation> taggedEntities = new HashSet<>();
		try {

			/** Search the query on bing */
			List<Pair<String, Integer>> bingBoldsAndRank = null;
			List<String> urls = null;
			List<String> relatedSearchRes = null;
			Triple<Integer, Double, JSONObject> resCountAndWebTotalNS = null;
			int resultsCount = -1;
			double webTotal = Double.NaN;
			List<String> filteredBolds = null;
			HashMap<Integer, Integer> rankToIdNS = null;
			if (includeSourceAnnotator || includeSourceWikiSearch
					|| includeSourceRelatedSearch || includeSourceNormalSearch) {
				bingBoldsAndRank = new Vector<>();
				urls = new Vector<>();
				relatedSearchRes = new Vector<>();
				resCountAndWebTotalNS = takeBingData(query, bingBoldsAndRank,
						urls, relatedSearchRes, Integer.MAX_VALUE, false);
				resultsCount = resCountAndWebTotalNS.getLeft();
				webTotal = resCountAndWebTotalNS.getMiddle();
				filteredBolds = boldFilter.filterBolds(query, bingBoldsAndRank,
						resultsCount);
				rankToIdNS = urlsToRankID(urls);

				if (debugger != null) {
					debugger.addBoldPositionEditDistance(query,
							bingBoldsAndRank);
					debugger.addBoldFilterOutput(query, filteredBolds);
					debugger.addSource2SearchResult(query, rankToIdNS, urls);
					debugger.addBingResponseNormalSearch(query,
							resCountAndWebTotalNS.getRight());

				}
			}

			/** Do the WikipediaSearch on bing. */
			List<String> wikiSearchUrls = new Vector<>();
			List<Pair<String, Integer>> bingBoldsAndRankWS = new Vector<>();
			HashMap<String, Pair<Integer, Integer>> annTitlesToIdAndRankWS = null;
			Triple<Integer, Double, JSONObject> resCountAndWebTotalWS = null;
			double webTotalWiki = Double.NaN;
			if (includeSourceWikiSearch | includeSourceNormalSearch) {
				resCountAndWebTotalWS = takeBingData(query, bingBoldsAndRankWS,
						wikiSearchUrls, null, topKWikiSearch, true);
				webTotalWiki = resCountAndWebTotalWS.getMiddle();
				HashMap<Integer, Integer> rankToIdWikiSearch = urlsToRankID(wikiSearchUrls);
				if (debugger != null) {
					debugger.addSource3SearchResult(query, rankToIdWikiSearch,
							wikiSearchUrls);
					debugger.addBingResponseWikiSearch(query,
							resCountAndWebTotalWS.getRight());

				}
				annTitlesToIdAndRankWS = adjustTitles(rankToIdWikiSearch);
			}

			/** Do the RelatedSearch on bing */
			String relatedSearch = null;
			List<String> relatedSearchUrls = null;
			List<Pair<String, Integer>> bingBoldsAndRankRS = null;
			HashMap<Integer, Integer> rankToIdRelatedSearch = null;
			HashMap<String, Pair<Integer, Integer>> annTitlesToIdAndRankRS = null;
			double webTotalRelatedSearch = Double.NaN;
			if (includeSourceRelatedSearch) {
				relatedSearch = getRelatedSearch(relatedSearchRes, query);
				relatedSearchUrls = new Vector<>();
				bingBoldsAndRankRS = new Vector<>();
				Triple<Integer, Double, JSONObject> resCountAndWebTotalRS = takeBingData(
						query, bingBoldsAndRankRS, relatedSearchUrls, null,
						topKRelatedSearch, false);
				webTotalRelatedSearch = resCountAndWebTotalRS.getMiddle();
				rankToIdRelatedSearch = urlsToRankID(relatedSearchUrls);
				annTitlesToIdAndRankRS = adjustTitles(rankToIdRelatedSearch);
			}

			/** Annotate bolds on the annotator */
			Pair<HashMap<String, HashMap<String, Double>>, HashMap<String, Annotation>> infoAndAnnotations = null;
			HashMap<String, Annotation> spotToAnnotation = null;
			HashMap<String, HashMap<String, Double>> additionalInfo = null;
			Pair<String, HashSet<Mention>> annInput = null;
			if (includeSourceAnnotator) {
				annInput = concatenateBolds(filteredBolds);
				infoAndAnnotations = disambiguateBolds(annInput.first,
						annInput.second);
				spotToAnnotation = infoAndAnnotations.second;
				additionalInfo = infoAndAnnotations.first;

				if (debugger != null)
					debugger.addReturnedAnnotation(query, spotToAnnotation);
			}

			// Filter and add annotations found by the disambiguator
			if (includeSourceAnnotator) {
				for (String bold : filteredBolds) {
					if (spotToAnnotation.containsKey(bold)) {
						Annotation ann = spotToAnnotation.get(bold);
						HashMap<String, Double> ESFeatures = generateEntitySelectionFeaturesAnnotator(
								query, resultsCount, ann, annInput,
								bingBoldsAndRank, additionalInfo);
						boolean accept = entityFilter.filterEntity(ESFeatures);
						if (accept)
							taggedEntities.add(new ScoredAnnotation(0, 1, ann
									.getConcept(), 0));
						if (debugger != null) {
							HashSet<String> bolds = new HashSet<>();
							bolds.add(bold);
							debugger.addQueryCandidateBolds(query, "Source 1",
									ann.getConcept(), bolds);
							debugger.addEntityFeaturesS1(query,
									ann.getConcept(), ESFeatures, accept);
							if (accept)
								debugger.addResult(query, ann.getConcept());
						}
					}
				}
			}

			// Filter and add entities found in the normal search
			if (includeSourceNormalSearch) {
				for (int rank : rankToIdNS.keySet()) {
					int wid = rankToIdNS.get(rank);
					HashMap<String, Double> ESFeatures = generateEntitySelectionFeaturesNormalSearch(
							query, wid, rank, webTotal, webTotalWiki);
					boolean accept = entityFilter.filterEntity(ESFeatures);
					if (accept)
						taggedEntities.add(new ScoredAnnotation(0, 1, wid, 0));
					if (debugger != null) {
						HashSet<String> bolds = new HashSet<>();
						for (Pair<String, Integer> boldRank : bingBoldsAndRank) {
							if (boldRank.second == rank)
								bolds.add(boldRank.first);
						}
						debugger.addQueryCandidateBolds(query, "Source 2", wid,
								bolds);
						debugger.addEntityFeaturesS2(query, wid, ESFeatures,
								accept);
						if (accept)
							debugger.addResult(query, wid);
					}
				}
			}

			// Filter and add entities found in the WikipediaSearch
			if (includeSourceWikiSearch) {
				for (String annotatedTitleWS : annTitlesToIdAndRankWS.keySet()) {
					int wid = annTitlesToIdAndRankWS.get(annotatedTitleWS).first;
					int rank = annTitlesToIdAndRankWS.get(annotatedTitleWS).second;
					HashMap<String, Double> ESFeatures = generateEntitySelectionFeaturesWikiSearch(
							query, wid, rank, webTotalWiki, bingBoldsAndRankWS,
							3);

					boolean accept = entityFilter.filterEntity(ESFeatures);
					if (accept)
						taggedEntities.add(new ScoredAnnotation(0, 1, wid, 0));
					if (debugger != null) {
						HashSet<String> bolds = new HashSet<>();
						for (Pair<String, Integer> boldRank : bingBoldsAndRankWS) {
							if (boldRank.second == rank)
								bolds.add(boldRank.first);
						}
						debugger.addQueryCandidateBolds(query, "Source 3", wid,
								bolds);
						debugger.addEntityFeaturesS3(query, wid, ESFeatures,
								accept);
						if (accept)
							debugger.addResult(query, wid);

					}
				}
			}

			// Filter and add entities found in the RelatedSearch
			if (includeSourceRelatedSearch) {
				for (String annotatedTitleRS : annTitlesToIdAndRankRS.keySet()) {
					int wid = annTitlesToIdAndRankRS.get(annotatedTitleRS).first;
					int rank = annTitlesToIdAndRankRS.get(annotatedTitleRS).second;
					HashMap<String, Double> ESFeatures = generateEntitySelectionFeaturesWikiSearch(
							relatedSearch, wid, rank, webTotalRelatedSearch,
							bingBoldsAndRankRS, 5);

					if (entityFilter.filterEntity(ESFeatures))
						taggedEntities.add(new ScoredAnnotation(0, 1, wid, 0));
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		SmaphAnnotatorDebugger.out.printf("*** END :%s ***%n", query);

		return taggedEntities;

	}

	/**
	 * @param relatedSearchRes
	 *            the related search suggested in the first query to Bing.
	 * @param query
	 *            the input query.
	 * @return the best related search for Source 4.
	 */
	private static String getRelatedSearch(List<String> relatedSearchRes,
			String query) {
		if (relatedSearchRes.isEmpty())
			return null;
		HashSet<String> qTokens = new HashSet<>(Arrays.asList(query
				.split("\\W+")));
		qTokens.remove("");
		Vector<String> rsTokens = new Vector<>(Arrays.asList(relatedSearchRes
				.get(0).split("\\s+")));
		rsTokens.remove("");

		String newSearch = "";
		int insertedTokens = 0;
		for (String rsToken : rsTokens)
			for (String qToken : qTokens)
				if (SmaphUtils.getNormEditDistance(qToken, rsToken) < 0.5) {
					newSearch += rsToken + " ";
					insertedTokens++;
					break;
				}
		if (insertedTokens == 0)
			return null;
		if (newSearch.isEmpty())
			return null;
		if (newSearch.charAt(newSearch.length() - 1) == ' ')
			newSearch = newSearch.substring(0, newSearch.length() - 1);
		return newSearch;
	}

	/**
	 * Adjust the title of retrieved Wikipedia pages, e.g. removing final
	 * parenthetical.
	 * 
	 * @param rankToIdWS
	 *            a mapping from a rank (position in the search engine result)
	 *            to the Wikipedia ID of the page in that rank.
	 * @return a mapping from adjusted titles to a pair <wid, rank>
	 */
	private HashMap<String, Pair<Integer, Integer>> adjustTitles(
			HashMap<Integer, Integer> rankToIdWS) {
		HashMap<String, Pair<Integer, Integer>> res = new HashMap<>();
		for (int rank : rankToIdWS.keySet()) {
			int wid = rankToIdWS.get(rank);
			try {
				String title = wikiApi.getTitlebyId(wid);
				if (title != null) {
					title = title.replaceAll(WIKITITLE_ENDPAR_REGEX, "");

					res.put(title, new Pair<Integer, Integer>(wid, rank));
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException();
			}
		}
		return res;
	}

	/**
	 * Concatenates the bolds passed by argument.
	 * 
	 * @param bolds
	 * @return the list of concatenated bolds and the set of their mentions.
	 */
	private static Pair<String, HashSet<Mention>> concatenateBolds(
			List<String> bolds) {
		HashSet<Mention> mentions = new HashSet<Mention>();
		String concat = "";
		for (String spot : bolds) {
			int mentionStart = concat.length();
			int mentionEnd = mentionStart + spot.length() - 1;
			mentions.add(new Mention(mentionStart, mentionEnd - mentionStart
					+ 1));
			concat += spot + " ";
		}
		return new Pair<String, HashSet<Mention>>(concat, mentions);
	}

	/**
	 * @param bingReply
	 *            Bing's reply.
	 * @return whether the query to bing failed and has to be re-issued.
	 * @throws JSONException
	 *             if the Bing result could not be read.
	 */
	private static boolean recacheNeeded(JSONObject bingReply)
			throws JSONException {
		if (bingReply == null)
			return true;
		JSONObject data = (JSONObject) bingReply.get("d");
		if (data == null)
			return true;
		JSONObject results = (JSONObject) ((JSONArray) data.get("results"))
				.get(0);
		if (results == null)
			return true;
		JSONArray webResults = (JSONArray) results.get("Web");
		if (webResults == null)
			return true;
		if (((String) results.get("WebTotal")).equals(""))
			return true;
		return false;
	}

	/**
	 * Issue a query to Bing and extract the result.
	 * 
	 * @param query
	 *            the query to be issued to Bing
	 * @param boldsAndRanks
	 *            storage for the bolds (a pair &lt;bold, rank&gt; means bold
	 *            appeared in the snippets of the result in position rank)
	 * @param urls
	 *            storage for the urls found by Bing.
	 * @param relatedSearch
	 *            storage for the "related search" suggestions.
	 * @return a triple &lt;results, webTotal, bingReply&gt; where results is
	 *         the number of results returned by Bing, webTotal is the number of
	 *         pages found by Bing, and bingReply is the raw Bing reply.
	 * @param topk
	 *            limit to top-k results.
	 * @param wikisearch
	 *            whether to append the word "wikipedia" to the query or not.
	 * @throws Exception
	 *             if something went wrong while querying Bing.
	 */

	private Triple<Integer, Double, JSONObject> takeBingData(String query,
			List<Pair<String, Integer>> boldsAndRanks, List<String> urls,
			List<String> relatedSearch, int topk, boolean wikisearch)
			throws Exception {
		if (!boldsAndRanks.isEmpty())
			throw new RuntimeException("boldsAndRanks must be empty");
		if (!urls.isEmpty())
			throw new RuntimeException("urls must be empty");
		if (wikisearch)
			query += " wikipedia";
		JSONObject bingReply = queryBing(query, BING_RETRY);
		JSONObject data = (JSONObject) bingReply.get("d");
		JSONObject results = (JSONObject) ((JSONArray) data.get("results"))
				.get(0);
		JSONArray webResults = (JSONArray) results.get("Web");
		double webTotal = new Double((String) results.get("WebTotal"));

		getBoldsAndUrls(webResults, topk, boldsAndRanks, urls);

		if (relatedSearch != null) {
			JSONArray relatedSearchResults = (JSONArray) results
					.get("RelatedSearch");
			for (int i = 0; i < relatedSearchResults.length(); i++) {
				JSONObject resI = (JSONObject) relatedSearchResults.get(i);
				String rsI = (String) resI.get("Title");
				relatedSearch.add(rsI);
			}
		}

		return new ImmutableTriple<Integer, Double, JSONObject>(
				webResults.length(), webTotal, bingReply);
	}

	/**
	 * Turns a Wikipedia URL to the title of the Wikipedia page.
	 * 
	 * @param encodedWikiUrl
	 * @return a Wikipedia title, or null if the url is not a Wikipedia page.
	 */
	private static String decodeWikiUrl(String encodedWikiUrl) {
		if (!encodedWikiUrl.matches("^" + WIKI_URL_LEADING + ".*")) {
			return null;
		}
		try {
			String title = URLDecoder.decode(
					encodedWikiUrl.substring(WIKI_URL_LEADING.length()),
					"utf-8");
			if (!SmaphUtils.acceptWikipediaTitle(title))
				return null;
			return title;

		} catch (IllegalArgumentException | UnsupportedEncodingException e) {
			return null;

		}

	}

	/**
	 * From the bing results extract the bolds and the urls.
	 * 
	 * @param webResults
	 *            the web results returned by Bing.
	 * @param topk
	 *            limit the extraction to the first topk results.
	 * @param boldsAndRanks
	 *            storage for the bolds and their rank.
	 * @param urls
	 *            storage for the result URLs.
	 * @throws JSONException
	 *             if the json returned by Bing could not be read.
	 */
	private static void getBoldsAndUrls(JSONArray webResults, double topk,
			List<Pair<String, Integer>> boldsAndRanks, List<String> urls)
			throws JSONException {
		for (int i = 0; i < Math.min(webResults.length(), topk); i++) {
			JSONObject resI = (JSONObject) webResults.get(i);
			String descI = (String) resI.get("Description");
			String url = (String) resI.get("Url");
			urls.add(url);

			byte[] startByte = new byte[] { (byte) 0xee, (byte) 0x80,
					(byte) 0x80 };
			byte[] stopByte = new byte[] { (byte) 0xee, (byte) 0x80,
					(byte) 0x81 };
			String start = new String(startByte);
			String stop = new String(stopByte);
			descI = descI.replaceAll(stop + "." + start, " ");
			int startIdx = descI.indexOf(start);
			int stopIdx = descI.indexOf(stop, startIdx);
			while (startIdx != -1 && stopIdx != -1) {
				String spot = descI.subSequence(startIdx + 1, stopIdx)
						.toString();
				boldsAndRanks.add(new Pair<String, Integer>(spot, i));
				SmaphAnnotatorDebugger.out.printf("Rank:%d Bold:%s%n", i, spot);
				startIdx = descI.indexOf(start, startIdx + 1);
				stopIdx = descI.indexOf(stop, startIdx + 1);
			}
		}
	}

	/**
	 * Issue the query to bing, return the json object.
	 * 
	 * @param query
	 *            the query.
	 * @param retryLeft
	 *            how many retry left we have (if zero, will return an empty
	 *            object in case of failure).
	 * @return the JSON object as returned by the Bing Api.
	 * @throws Exception
	 *             is the call to the API failed.
	 */
	private synchronized JSONObject queryBing(String query, int retryLeft)
			throws Exception {
		boolean forceCacheOverride = retryLeft < BING_RETRY;
		if (forceCacheOverride)
			Thread.sleep(1000);
		String accountKeyAuth = Base64.encode(
				(bingKey + ":" + bingKey).getBytes(), 0);

		URL url = new URL(
				"https://api.datamarket.azure.com/Bing/Search/v1/Composite?Sources=%27web%2Bspell%2BRelatedSearch%27&Query=%27"
						+ URLEncoder.encode(query, "utf8")
						+ "%27&Options=%27EnableHighlighting%27&Market=%27en-US%27&Adult=%27Off%27&$format=Json");

		boolean cached = !forceCacheOverride
				&& url2jsonCache.containsKey(url.toExternalForm());
		SmaphAnnotatorDebugger.out.printf("%s%s %s%n",
				forceCacheOverride ? "<forceCacheOverride>" : "",
				cached ? "<cached>" : "Querying", url);
		if (!cached) {
			HttpURLConnection connection = (HttpURLConnection) url
					.openConnection();
			connection.setConnectTimeout(0);
			connection.setRequestProperty("Authorization", "Basic "
					+ accountKeyAuth);
			connection.setRequestProperty("Accept", "*/*");
			connection
					.setRequestProperty("Content-Type", "multipart/form-data");

			connection.setUseCaches(false);

			if (connection.getResponseCode() != 200) {
				Scanner s = new Scanner(connection.getErrorStream())
						.useDelimiter("\\A");
				System.err.printf("Got HTTP error %d. Message is: %s%n",
						connection.getResponseCode(), s.next());
				s.close();
				throw new RuntimeException("Got response code:"
						+ connection.getResponseCode());
			}

			Scanner s = new Scanner(connection.getInputStream())
					.useDelimiter("\\A");
			String resultStr = s.hasNext() ? s.next() : "";
			url2jsonCache.put(url.toExternalForm(), new JSONObject(resultStr));
			increaseFlushCounter();
		}

		if (recacheNeeded(url2jsonCache.get(url.toExternalForm()))
				&& retryLeft > 0)
			return queryBing(query, retryLeft - 1);

		return url2jsonCache.get(url.toExternalForm());
	}

	/**
	 * Set the file to which the Bing responses cache is bound.
	 * 
	 * @param cacheFilename
	 *            the cache file name.
	 * @throws FileNotFoundException
	 *             if the file could not be open for reading.
	 * @throws IOException
	 *             if something went wrong while reading the file.
	 * @throws ClassNotFoundException
	 *             is the file contained an object of the wrong class.
	 */
	public static void setCache(String cacheFilename)
			throws FileNotFoundException, IOException, ClassNotFoundException {
		System.out.println("Loading bing cache...");
		resultsCacheFilename = cacheFilename;
		if (new File(resultsCacheFilename).exists()) {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
					resultsCacheFilename));
			url2jsonCache = (HashMap<String, JSONObject>) ois.readObject();
			ois.close();
		}
	}

	/**
	 * Clear the Bing response cache and call the garbage collector.
	 */
	public static void unSetCache() {
		url2jsonCache = new HashMap<>();
		System.gc();
	}

	/**
	 * Given a list of urls, creates a mapping from the url position to the
	 * Wikipedia page ID of that URL. If an url is not a Wikipedia url, no
	 * mapping is added.
	 * 
	 * @param urls
	 *            a list of urls.
	 * @return a mapping from position to Wikipedia page IDs.
	 */
	private HashMap<Integer, Integer> urlsToRankID(List<String> urls) {
		HashMap<Integer, Integer> result = new HashMap<>();
		HashMap<Integer, String> rankToTitle = new HashMap<>();
		for (int i = 0; i < urls.size(); i++) {
			String title = decodeWikiUrl(urls.get(i));
			if (title != null)
				rankToTitle.put(i, title);
		}

		try {
			wikiApi.prefetchTitles(new Vector<String>(rankToTitle.values()));
		} catch (XPathExpressionException | IOException
				| ParserConfigurationException | SAXException e) {
			throw new RuntimeException(e);
		}
		for (int rank : rankToTitle.keySet()) {
			int wid;
			try {
				wid = wikiApi.getIdByTitle(rankToTitle.get(rank));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			if (wid != -1) {
				result.put(rank, wid);
				SmaphAnnotatorDebugger.out.printf(
						"Found Wikipedia url:%s rank:%d id:%d%n",
						urls.get(rank), rank, wid);
			} else
				SmaphAnnotatorDebugger.out.printf(
						"Discarding Wikipedia url:%s rank:%d id:%d%n",
						urls.get(rank), rank, wid);
		}
		return result;
	}

	/**
	 * Generates the Entity Selection features for an entity drawn from Source 1
	 * (Annotator)
	 * 
	 * @param query
	 *            the query that has been issued to Bing.
	 * @param resultsCount
	 *            the number of results contained in the Bing response.
	 * @param ann
	 *            the annotation from which the URL is extracted.
	 * @param annInput
	 *            the input that has been passed to the auxiliary annotator.
	 * @param bingBolds
	 *            the list of bolds spotted by Bing plus their position.
	 * @param additionalInfo
	 *            additional info returned by the annotator.
	 * @return a mapping between feature name and its value.
	 */
	private HashMap<String, Double> generateEntitySelectionFeaturesAnnotator(
			String query, int resultsCount, Annotation ann,
			Pair<String, HashSet<Mention>> annInput,
			List<Pair<String, Integer>> bingBolds,
			HashMap<String, HashMap<String, Double>> additionalInfo) {
		HashMap<String, Double> result = new HashMap<>();

		String bold = annInput.first.substring(ann.getPosition(),
				ann.getPosition() + ann.getLength());
		result.put("is_s1", 1.0);
		result.put("s1_freq",
				FrequencyBoldFilter.getFrequency(bingBolds, bold, resultsCount));
		result.put("s1_avgRank",
				RankWeightBoldFilter.getAvgRank(bingBolds, bold, resultsCount));

		result.put("s1_editDistance", SmaphUtils.getMinEditDist(query, bold));

		// Add additional info like rho, commonness, etc.
		for (String key : additionalInfo.get(bold).keySet())
			result.put("s1_" + key, additionalInfo.get(bold).get(key));

		return result;

	}

	/**
	 * Generates the Entity Selection features for an entity drawn from Source 2
	 * (Normal Search)
	 * 
	 * @param query
	 *            the query that has been issued to Bing.
	 * @param wid
	 *            the Wikipedia page ID of the entity.
	 * @param rank
	 *            the position in which the entity appeared in the Bing results.
	 * @param webTotalWiki
	 *            total web results found by Bing for the Wikisearch.
	 * @param webTotal
	 *            total web results found by Bing for the normal search.
	 * @return a mapping between feature name and its value.
	 */
	private HashMap<String, Double> generateEntitySelectionFeaturesNormalSearch(
			String query, int wid, int rank, double webTotalWiki,
			double webTotal) {
		HashMap<String, Double> result = new HashMap<>();
		result.put("is_s2", 1.0);
		try {
			result.put("s2_editDistance",
					SmaphUtils.getMinEditDist(query, wikiApi.getTitlebyId(wid)));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		result.put("s2_rank", (double) rank);
		result.put("s2_webTotalWiki", (double) webTotalWiki);
		result.put("s2_webTotal", (double) webTotal);

		return result;

	}

	/**
	 * Generates the Entity Selection features for an entity drawn from Source 2
	 * (Normal Search)
	 * 
	 * @param query
	 *            the query that has been issued to Bing.
	 * @param wid
	 *            the Wikipedia page ID of the entity.
	 * @param rank
	 *            the position in which the entity appeared in the Bing results.
	 * @param wikiWebTotal
	 *            total web results found by Bing for the Wikisearch.
	 * @param bingBoldsWS
	 * @param source
	 *            Source id (3 for WikiSearch)
	 * @return a mapping between feature name and its value.
	 */
	private HashMap<String, Double> generateEntitySelectionFeaturesWikiSearch(
			String query, int wid, int rank, double wikiWebTotal,
			List<Pair<String, Integer>> bingBoldsWS, int source) {

		String sourceName = "s" + source;
		HashMap<String, Double> result = new HashMap<>();
		result.put("is_" + sourceName, 1.0);
		result.put(sourceName + "_rank", (double) rank);
		result.put(sourceName + "_wikiWebTotal", (double) wikiWebTotal);
		String title;
		try {
			title = wikiApi.getTitlebyId(wid);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		result.put(sourceName + "_editDistanceTitle",
				SmaphUtils.getMinEditDist(query, title));
		result.put(
				sourceName + "_editDistanceNoPar",
				SmaphUtils.getMinEditDist(query,
						title.replaceAll(WIKITITLE_ENDPAR_REGEX, "")));

		double minEdDist = 1.0;
		double capitalized = 0;
		double avgNumWords = 0;
		int boldsCount = 0;
		for (Pair<String, Integer> p : bingBoldsWS)
			if (p.second == rank) {
				boldsCount++;
				minEdDist = Math.min(minEdDist,
						SmaphUtils.getMinEditDist(query, p.first));
				if (Character.isUpperCase(p.first.charAt(0)))
					capitalized++;
				avgNumWords += p.first.split("\\W+").length;
			}
		if (boldsCount != 0)
			avgNumWords /= boldsCount;
		result.put(sourceName + "_editDistanceBolds", minEdDist);
		result.put(sourceName + "_capitalizedBolds", capitalized);
		result.put(sourceName + "_avgBoldsWords", avgNumWords);

		return result;
	}

	/**
	 * Given a query and its gold standard, generate
	 * 
	 * @param query
	 *            a query.
	 * @param goldStandard
	 *            the entities associated to the query.
	 * @param posEFVectors
	 *            where to store the positive-example (true positives) feature
	 *            vectors.
	 * @param negEFVectors
	 *            where to store the negative-example (false positives) feature
	 *            vectors.
	 * @param discardNE
	 *            whether to limit the output to named entities, as defined by
	 *            ERDDatasetFilter.EntityIsNE.
	 * @param wikiToFreeb
	 *            a wikipedia to freebase-id mapping.
	 * @throws Exception
	 *             if something went wrong while annotating the query.
	 */
	public void generateExamples(String query, HashSet<Tag> goldStandard,
			Vector<double[]> posEFVectors, Vector<double[]> negEFVectors,
			boolean discardNE, WikipediaToFreebase wikiToFreeb)
			throws Exception {

		/** Search the query on bing */
		List<Pair<String, Integer>> bingBoldsAndRank = null;
		List<String> urls = null;
		List<String> relatedSearchRes = null;
		Triple<Integer, Double, JSONObject> resCountAndWebTotal = null;
		int resultsCount = -1;
		double webTotal = Double.NaN;
		List<String> filteredBolds = null;
		HashMap<Integer, Integer> rankToIdNS = null;
		if (includeSourceAnnotator || includeSourceWikiSearch
				|| includeSourceRelatedSearch || includeSourceNormalSearch) {
			bingBoldsAndRank = new Vector<>();
			urls = new Vector<>();
			relatedSearchRes = new Vector<>();
			resCountAndWebTotal = takeBingData(query, bingBoldsAndRank, urls,
					relatedSearchRes, Integer.MAX_VALUE, false);
			resultsCount = resCountAndWebTotal.getLeft();
			webTotal = resCountAndWebTotal.getMiddle();
			filteredBolds = boldFilter.filterBolds(query, bingBoldsAndRank,
					resultsCount);
			rankToIdNS = urlsToRankID(urls);

			if (debugger != null) {
				debugger.addBoldPositionEditDistance(query, bingBoldsAndRank);
				debugger.addBoldFilterOutput(query, filteredBolds);
				debugger.addSource2SearchResult(query, rankToIdNS, urls);
				debugger.addBingResponseNormalSearch(query,
						resCountAndWebTotal.getRight());

			}
		}

		/** Do the wikipedia-search on bing. */
		List<String> wikiSearchUrls = new Vector<>();
		List<Pair<String, Integer>> bingBoldsAndRankWS = new Vector<>();
		HashMap<String, Pair<Integer, Integer>> annTitlesToIdAndRankWS = null;
		Triple<Integer, Double, JSONObject> resCountAndWebTotalWS = null;
		double webTotalWiki = Double.NaN;
		if (includeSourceWikiSearch | includeSourceNormalSearch) {
			resCountAndWebTotalWS = takeBingData(query, bingBoldsAndRankWS,
					wikiSearchUrls, null, topKWikiSearch, true);
			webTotalWiki = resCountAndWebTotalWS.getMiddle();
			HashMap<Integer, Integer> rankToIdWikiSearch = urlsToRankID(wikiSearchUrls);
			if (debugger != null) {
				debugger.addSource3SearchResult(query, rankToIdWikiSearch,
						wikiSearchUrls);
				debugger.addBingResponseWikiSearch(query,
						resCountAndWebTotal.getRight());

			}
			annTitlesToIdAndRankWS = adjustTitles(rankToIdWikiSearch);
		}

		/** Do the RelatedSearch on bing */
		String relatedSearch = null;
		List<String> relatedSearchUrls = null;
		List<Pair<String, Integer>> bingBoldsAndRankRS = null;
		HashMap<Integer, Integer> rankToIdRelatedSearch = null;
		HashMap<String, Pair<Integer, Integer>> annTitlesToIdAndRankRS = null;
		double webTotalRelatedSearch = Double.NaN;
		if (includeSourceRelatedSearch) {
			relatedSearch = getRelatedSearch(relatedSearchRes, query);
			relatedSearchUrls = new Vector<>();
			bingBoldsAndRankRS = new Vector<>();
			Triple<Integer, Double, JSONObject> resCountAndWebTotalRS = takeBingData(
					query, bingBoldsAndRankRS, relatedSearchUrls, null,
					topKRelatedSearch, false);
			webTotalRelatedSearch = resCountAndWebTotalRS.getMiddle();
			rankToIdRelatedSearch = urlsToRankID(relatedSearchUrls);
			annTitlesToIdAndRankRS = adjustTitles(rankToIdRelatedSearch);
		}

		/** Annotate bolds on the annotator */
		Pair<HashMap<String, HashMap<String, Double>>, HashMap<String, Annotation>> infoAndAnnotations = null;
		HashMap<String, Annotation> spotToAnnotation = null;
		HashMap<String, HashMap<String, Double>> additionalInfo = null;
		Pair<String, HashSet<Mention>> annInput = null;
		if (includeSourceAnnotator) {
			annInput = concatenateBolds(filteredBolds);
			infoAndAnnotations = disambiguateBolds(annInput.first,
					annInput.second);
			spotToAnnotation = infoAndAnnotations.second;
			additionalInfo = infoAndAnnotations.first;

			if (debugger != null)
				debugger.addReturnedAnnotation(query, spotToAnnotation);
		}

		List<Pair<Tag, HashMap<String, Double>>> widToEFFtrVect = new Vector<>();
		// Filter and add annotations found by the disambiguator
		if (includeSourceAnnotator) {
			for (String bold : filteredBolds) {
				if (spotToAnnotation.containsKey(bold)) {
					Annotation ann = spotToAnnotation.get(bold);
					HashMap<String, Double> ESFeatures = generateEntitySelectionFeaturesAnnotator(
							query, resultsCount, ann, annInput,
							bingBoldsAndRank, additionalInfo);
					Tag tag = new Tag(ann.getConcept());
					widToEFFtrVect.add(new Pair<Tag, HashMap<String, Double>>(
							tag, ESFeatures));
				}
			}
		}

		// Filter and add entities found in the normal search
		if (includeSourceNormalSearch) {
			for (int rank : rankToIdNS.keySet()) {
				int wid = rankToIdNS.get(rank);
				HashMap<String, Double> ESFeatures = generateEntitySelectionFeaturesNormalSearch(
						query, wid, rank, webTotal, webTotalWiki);
				Tag tag = new Tag(wid);
				widToEFFtrVect.add(new Pair<Tag, HashMap<String, Double>>(tag,
						ESFeatures));
			}
		}

		// Filter and add entities found in the WikipediaSearch
		if (includeSourceWikiSearch) {
			for (String annotatedTitleWS : annTitlesToIdAndRankWS.keySet()) {
				int wid = annTitlesToIdAndRankWS.get(annotatedTitleWS).first;
				int rank = annTitlesToIdAndRankWS.get(annotatedTitleWS).second;
				HashMap<String, Double> ESFeatures = generateEntitySelectionFeaturesWikiSearch(
						query, wid, rank, webTotalWiki, bingBoldsAndRankWS, 3);

				Tag tag = new Tag(wid);
				widToEFFtrVect.add(new Pair<Tag, HashMap<String, Double>>(tag,
						ESFeatures));
			}
		}

		// Filter and add entities found in the RelatedSearch
		if (includeSourceRelatedSearch) {
			for (String annotatedTitleRS : annTitlesToIdAndRankRS.keySet()) {
				int wid = annTitlesToIdAndRankRS.get(annotatedTitleRS).first;
				int rank = annTitlesToIdAndRankRS.get(annotatedTitleRS).second;
				HashMap<String, Double> ESFeatures = generateEntitySelectionFeaturesWikiSearch(
						relatedSearch, wid, rank, webTotalRelatedSearch,
						bingBoldsAndRankRS, 5);

				Tag tag = new Tag(wid);
				widToEFFtrVect.add(new Pair<Tag, HashMap<String, Double>>(tag,
						ESFeatures));
			}
		}

		for (Pair<Tag, HashMap<String, Double>> tagAndFtrs : widToEFFtrVect) {
			Tag tag = tagAndFtrs.first;
			HashMap<String, Double> ftrs = tagAndFtrs.second;
			if (discardNE
					&& !ERDDatasetFilter.EntityIsNE(wikiApi, wikiToFreeb,
							tag.getConcept()))
				continue;

			if (goldStandard.contains(tag))
				posEFVectors.add(LibSvmEntityFilter
						.featuresToFtrVectStatic(ftrs));
			else
				negEFVectors.add(LibSvmEntityFilter
						.featuresToFtrVectStatic(ftrs));
			System.out.printf("%d in query [%s] is a %s example.%n", tag
					.getConcept(), query,
					goldStandard.contains(tag) ? "positive" : "negative");
		}
	}

}
