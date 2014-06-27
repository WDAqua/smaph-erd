package it.acubelab.erd;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.tartarus.snowball.ext.EnglishStemmer;
import org.xml.sax.SAXException;

import com.sun.org.apache.xml.internal.security.utils.Base64;

import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.data.Mention;
import it.acubelab.batframework.data.ScoredAnnotation;
import it.acubelab.batframework.data.ScoredTag;
import it.acubelab.batframework.data.Tag;
import it.acubelab.batframework.problems.D2WSystem;
import it.acubelab.batframework.problems.Sa2WSystem;
import it.acubelab.batframework.utils.AnnotationException;
import it.acubelab.batframework.utils.FreebaseApi;
import it.acubelab.batframework.utils.Pair;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.erd.emptyqueryfilters.EmptyQueryFilter;
import it.acubelab.erd.emptyqueryfilters.LibSvmEmptyQueryFilter;
import it.acubelab.erd.emptyqueryfilters.NoEmptyQueryFilter;
import it.acubelab.erd.entityfilters.EntityFilter;
import it.acubelab.erd.entityfilters.LibSvmEntityFilter;
import it.acubelab.erd.entityfilters.NoEntityFilter;
import it.acubelab.erd.learn.BinaryExampleGatherer;
import it.acubelab.erd.main.ERDDatasetFilter;
import it.acubelab.erd.spotfilters.FrequencySpotFilter;
import it.acubelab.erd.spotfilters.RankWeightSpotFilter;
import it.acubelab.erd.spotfilters.SpotFilter;
import it.acubelab.tagme.develexp.WikiSenseAnnotatorDevelopment;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

public class BingAnnotator implements Sa2WSystem {
	private static final String WIKI_URL_LEADING = "http://en.wikipedia.org/wiki/";
	private static final int BING_RETRY = 3;
	private String bingKey;
	private static final int FLUSH_EVERY = 50;
	public static final String WIKITITLE_ENDPAR_REGEX = "\\s*\\([^\\)]*\\)\\s*$";
	private static HashMap<String, JSONObject> url2jsonCache = new HashMap<>();
	private static String resultsCacheFilename;
	private static int flushCounter = 0;
	private WikipediaApiInterface wikiApi;

	private WikiSenseAnnotatorDevelopment auxDisambiguator;
	private SpotFilter boldFilter;
	private EntityFilter entityFilter;
	private EmptyQueryFilter emptyQueryFilter;
	private boolean includeSourceNormalSearch;
	private boolean includeSourceAnnotator;
	private boolean includeSourceAnnotatorTopK;
	private boolean includeSourceWikiSearch;
	private int topKWikiSearch = 0;
	private int topKAnnotatorCandidates = 0;
	private boolean includeSourceRelatedSearch;
	private int topKRelatedSearch;

	public BingAnnotator(WikiSenseAnnotatorDevelopment auxDisambiguator,
			SpotFilter spotManager, WikipediaApiInterface wikiApi, String bingKey) {
		this(auxDisambiguator, spotManager, new NoEntityFilter(),
				new NoEmptyQueryFilter(), true, false, false, 0, false, 0,
				false, 0, wikiApi, bingKey);
	}

	public BingAnnotator(WikiSenseAnnotatorDevelopment auxDisambiguator,
			SpotFilter spotManager, EntityFilter entityFilter,
			EmptyQueryFilter emptyQueryFilter, boolean includeSourceAnnotator,
			boolean includeSourceNormalSearch, boolean includeSourceWikiSearch,
			int wikiSearchPages, boolean includeSourceAnnotatorTopK,
			int topKAnnotatorCandidates, boolean includeRelatedSearch,
			int topKRelatedSearch, WikipediaApiInterface wikiApi, String bingKey) {
		this.auxDisambiguator = auxDisambiguator;
		this.boldFilter = spotManager;
		this.entityFilter = entityFilter;
		this.wikiApi = wikiApi;
		this.emptyQueryFilter = emptyQueryFilter;
		this.includeSourceAnnotator = includeSourceAnnotator;
		this.includeSourceNormalSearch = includeSourceNormalSearch;
		this.includeSourceWikiSearch = includeSourceWikiSearch;
		this.includeSourceAnnotatorTopK = includeSourceAnnotatorTopK;
		this.topKWikiSearch = wikiSearchPages;
		this.topKAnnotatorCandidates = topKAnnotatorCandidates;
		this.includeSourceRelatedSearch = includeRelatedSearch;
		this.topKRelatedSearch = topKRelatedSearch;
		this.bingKey = bingKey;
	}

	public static synchronized void increaseFlushCounter()
			throws FileNotFoundException, IOException {
		flushCounter++;
		if (flushCounter % FLUSH_EVERY == 0)
			flush();
	}

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
		return null;
	}

	@Override
	public String getName() {
		return "Bing annotator";
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

	private Triple<HashMap<String, HashMap<String, Double>>, HashMap<String, Annotation>, HashMap<String, List<HashMap<String, Double>>>> solveD2WDisambiguator(
			String text, HashSet<Mention> mentions) throws IOException,
			XPathExpressionException, ParserConfigurationException,
			SAXException {
		HashSet<Annotation> anns;
		anns = auxDisambiguator.solveD2W(text, mentions);
		if (anns == null)
			return new ImmutableTriple<HashMap<String, HashMap<String, Double>>, HashMap<String, Annotation>, HashMap<String, List<HashMap<String, Double>>>>(
					new HashMap<String, HashMap<String, Double>>(),
					new HashMap<String, Annotation>(),
					new HashMap<String, List<HashMap<String, Double>>>());

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
		return new ImmutableTriple<HashMap<String, HashMap<String, Double>>, HashMap<String, Annotation>, HashMap<String, List<HashMap<String, Double>>>>(
				auxDisambiguator.getLastQueryAdditionalInfo(),
				spotToAnnotation, additionalCandidatesInfo);
	}

	@Override
	public HashSet<ScoredAnnotation> solveSa2W(String query)
			throws AnnotationException {
		SmaphAnnotatorDebugger.out.printf("*** START :%s ***%n", query);

		HashSet<ScoredAnnotation> taggedEntities = new HashSet<>();
		try {

			/** Search the query on bing */
			List<Pair<String, Integer>> bingBoldsAndRank = null;
			List<String> urls = null;
			List<String> relatedSearchRes = null;
			Pair<Integer, Double> resCountAndWebTotal = null;
			int resultsCount = -1;
			double webTotal = Double.NaN;
			List<String> filteredBolds = null;
			if (includeSourceAnnotator || includeSourceAnnotatorTopK
					|| includeSourceWikiSearch || includeSourceRelatedSearch
					|| includeSourceNormalSearch) {
				bingBoldsAndRank = new Vector<>();
				urls = new Vector<>();
				relatedSearchRes = new Vector<>();
				resCountAndWebTotal = takeBingData(query, bingBoldsAndRank,
						urls, relatedSearchRes);
				resultsCount = resCountAndWebTotal.first;
				webTotal = resCountAndWebTotal.second;
				filteredBolds = boldFilter.filterBolds(query, bingBoldsAndRank,
						resultsCount);
			}

			/** Do the wikipedia-search on bing. */
			List<String> wikiSearchUrls = new Vector<>();
			List<Pair<String, Integer>> bingBoldsAndRankWS = new Vector<>();
			HashMap<String, Pair<Integer, Integer>> annTitlesToIdAndRankWS = null;
			double webTotalWiki = Double.NaN;
			if (includeSourceWikiSearch | includeSourceNormalSearch) {
				webTotalWiki = takeBingWikiResults(query, wikiSearchUrls,
						bingBoldsAndRankWS, topKWikiSearch);
				HashMap<Integer, Integer> rankToIdWikiSearch = urlsToRankID(wikiSearchUrls);
				annTitlesToIdAndRankWS = getTitlesForAnnotator(rankToIdWikiSearch);
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
				webTotalRelatedSearch = takeBingWikiResults(relatedSearch,
						relatedSearchUrls, bingBoldsAndRankRS,
						topKRelatedSearch);
				rankToIdRelatedSearch = urlsToRankID(relatedSearchUrls);
				annTitlesToIdAndRankRS = getTitlesForAnnotator(rankToIdRelatedSearch);
			}

			/** Annotate bolds on the annotator */
			Triple<HashMap<String, HashMap<String, Double>>, HashMap<String, Annotation>, HashMap<String, List<HashMap<String, Double>>>> infoAndAnnotations = null;
			HashMap<String, Annotation> spotToAnnotation = null;
			HashMap<String, HashMap<String, Double>> additionalInfo = null;
			HashMap<String, List<HashMap<String, Double>>> candidatesInfo = null;
			Pair<String, HashSet<Mention>> annInput = null;
			if (includeSourceAnnotator || includeSourceAnnotatorTopK) {
				annInput = generateAnnotatorInput(filteredBolds, null);
				infoAndAnnotations = solveD2WDisambiguator(annInput.first,
						annInput.second);
				spotToAnnotation = infoAndAnnotations.getMiddle();
				additionalInfo = infoAndAnnotations.getLeft();
				candidatesInfo = infoAndAnnotations.getRight();
			}

			HashMap<String, Double> EQFeatures = generateEmptyQueryFeatures(
					resultsCount, webTotal, bingBoldsAndRank, urls,
					wikiSearchUrls, webTotalWiki);
			if (!emptyQueryFilter.filterQuery(EQFeatures))
				return taggedEntities;

			// Filter and add annotations found by the disambiguator
			if (includeSourceAnnotator) {
				for (String bold : filteredBolds) {
					if (spotToAnnotation.containsKey(bold)) {
						Annotation ann = spotToAnnotation.get(bold);
						HashMap<String, Double> ESFeatures = generateEntitySelectionFeaturesAnnotation(
								query, resultsCount, ann, annInput,
								bingBoldsAndRank, additionalInfo);
						if (entityFilter.filterEntity(ESFeatures))
							taggedEntities.add(new ScoredAnnotation(0, 1, ann
									.getConcept(), 0));
					}
				}
			}

			// Filter and add entities found by the disambiguator, TOP-K
			if (includeSourceAnnotatorTopK) {
				for (String mention : candidatesInfo.keySet())
					for (HashMap<String, Double> candidateData : candidatesInfo
							.get(mention)) {
						if (candidateData.get("rank") >= topKAnnotatorCandidates)
							continue;
						HashMap<String, Double> ESFeatures = generateEntitySelectionFeaturesAnnotationCandidates(
								query, mention, candidateData);
						Tag tag = new Tag(candidateData.get("id").intValue());
						if (entityFilter.filterEntity(ESFeatures))
							taggedEntities.add(new ScoredAnnotation(0, 1, tag
									.getConcept(), 0));
					}
			}

			// Filter and add entities found in the normal search
			if (includeSourceNormalSearch) {
				HashMap<Integer, Integer> rankToId = urlsToRankID(urls);
				for (int rank : rankToId.keySet()) {
					int wid = rankToId.get(rank);
					HashMap<String, Double> ESFeatures = generateEntitySelectionFeaturesNormalSearch(
							query, wid, rank, webTotal, webTotalWiki);
					if (entityFilter.filterEntity(ESFeatures))
						taggedEntities.add(new ScoredAnnotation(0, 1, wid, 0));
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

					if (entityFilter.filterEntity(ESFeatures))
						taggedEntities.add(new ScoredAnnotation(0, 1, wid, 0));
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

	private HashMap<String, Double> getCandidateData(
			HashMap<String, List<HashMap<String, Double>>> candidatesInfoAllCandidatesWS,
			String title, int wid) {
		List<HashMap<String, Double>> WSCandidateData = candidatesInfoAllCandidatesWS
				.get(title);
		if (WSCandidateData == null) {
			SmaphAnnotatorDebugger.out
					.printf("ERROR: annotator did not tag title %s found by Wikisearch.%n",
							title);
			return null;
		}
		int i = 0;
		while (i < WSCandidateData.size()
				&& WSCandidateData.get(i).get("id").intValue() != wid)
			i++;
		if (i == WSCandidateData.size()) {
			SmaphAnnotatorDebugger.out
					.printf("INFO: page id %d does not appear in the candidates of %s.%n",
							wid, title);
			return null;
		}
		return WSCandidateData.get(i);
	}

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

	private HashMap<String, Pair<Integer, Integer>> getTitlesForAnnotator(
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

	private static Pair<String, HashSet<Mention>> generateAnnotatorInput(
			List<String> spots, Set<String> titlesWS) {
		Vector<String> allSpots = new Vector<>();
		/*
		 * HashSet<String> insertedStems = new HashSet<>(); EnglishStemmer
		 * stemmer = new EnglishStemmer(); for (String spot : spots) { String
		 * stemmedString = SmaphUtils.stemString(spot, stemmer) .toLowerCase();
		 * if (insertedStems.contains(stemmedString)) continue;
		 * insertedStems.add(stemmedString); allSpots.add(spot); }
		 */
		allSpots.addAll(spots);

		if (titlesWS != null)
			allSpots.addAll(titlesWS);
		HashSet<Mention> mentions = new HashSet<Mention>();
		String concat = "";
		for (String spot : allSpots) {
			int mentionStart = concat.length();
			int mentionEnd = mentionStart + spot.length() - 1;
			mentions.add(new Mention(mentionStart, mentionEnd - mentionStart
					+ 1));
			concat += spot + " ";
			// System.out.printf("%s %d %d%n",concat, mentionStart, mentionEnd);
		}
		return new Pair<String, HashSet<Mention>>(concat, mentions);
	}

	private static boolean recacheNeeded(JSONObject bingReply) {
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

	private double takeBingWikiResults(String query, List<String> result,
			List<Pair<String, Integer>> bingBoldsAndRankWS, int topk)
			throws Exception {
		if (!bingBoldsAndRankWS.isEmpty())
			throw new RuntimeException("result must be empty");

		if (!result.isEmpty())
			throw new RuntimeException("result must be empty");
		SmaphAnnotatorDebugger.out
				.println("*** Taking Bing results with +wikipedia ***");
		JSONObject bingReply = queryBing(query + " wikipedia", BING_RETRY);
		JSONObject data = (JSONObject) bingReply.get("d");
		JSONObject results = (JSONObject) ((JSONArray) data.get("results"))
				.get(0);
		JSONArray webResults = (JSONArray) results.get("Web");
		double webTotal = new Double((String) results.get("WebTotal"));

		for (int i = 0; i < Math.min(webResults.size(), topk); i++) {
			JSONObject resI = (JSONObject) webResults.get(i);
			String descI = (String) resI.get("Description");
			String url = (String) resI.get("Url");
			result.add(url);
			addBolds(bingBoldsAndRankWS, descI, i);
		}

		SmaphAnnotatorDebugger.out.println("Wiki WebTotal:" + webTotal);

		return webTotal;
	}

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
			SmaphAnnotatorDebugger.disable();
			System.out.println("Accepting Wikipedia page: " + title);
			return title;

		} catch (IllegalArgumentException | UnsupportedEncodingException e) {
			return null;

		}

	}

	private static void addBolds(List<Pair<String, Integer>> boldsAndRanks,
			String snippets, int rank) {
		byte[] startByte = new byte[] { (byte) 0xee, (byte) 0x80, (byte) 0x80 };
		byte[] stopByte = new byte[] { (byte) 0xee, (byte) 0x80, (byte) 0x81 };
		String start = new String(startByte);
		String stop = new String(stopByte);
		// System.out.println(descI);
		snippets = snippets.replaceAll(stop + "." + start, " ");
		// System.out.println(descI);
		int startIdx = snippets.indexOf(start);
		int stopIdx = snippets.indexOf(stop, startIdx);
		while (startIdx != -1 && stopIdx != -1) {
			String spot = snippets.subSequence(startIdx + 1, stopIdx)
					.toString();
			boldsAndRanks.add(new Pair<String, Integer>(spot, rank));
			SmaphAnnotatorDebugger.out.printf("Rank:%d Bold:%s%n", rank, spot);
			// System.out.printf("%d %d %s%n", startIdx+1, stopIdx, spot);
			startIdx = snippets.indexOf(start, startIdx + 1);
			stopIdx = snippets.indexOf(stop, startIdx + 1);
		}
	}

	private Pair<Integer, Double> takeBingData(String query,
			List<Pair<String, Integer>> result, List<String> urls,
			List<String> relatedSearch) throws Exception {
		if (!result.isEmpty())
			throw new RuntimeException("result must be empty");
		JSONObject bingReply = queryBing(query, BING_RETRY);
		JSONObject data = (JSONObject) bingReply.get("d");
		JSONObject results = (JSONObject) ((JSONArray) data.get("results"))
				.get(0);
		JSONArray webResults = (JSONArray) results.get("Web");
		double webTotal = new Double((String) results.get("WebTotal"));

		SmaphAnnotatorDebugger.out.println("*** Take Bing bolds ***");
		for (int i = 0; i < webResults.size(); i++) {
			JSONObject resI = (JSONObject) webResults.get(i);
			String descI = (String) resI.get("Description");
			String url = (String) resI.get("Url");
			urls.add(url);
			SmaphAnnotatorDebugger.out.printf("Rank:%d URL=%s%n", i, url);
			addBolds(result, descI, i);
		}
		JSONArray relatedSearchResults = (JSONArray) results
				.get("RelatedSearch");
		for (int i = 0; i < relatedSearchResults.size(); i++) {
			JSONObject resI = (JSONObject) relatedSearchResults.get(i);
			String rsI = (String) resI.get("Title");
			relatedSearch.add(rsI);
		}

		SmaphAnnotatorDebugger.out.printf("WebResults:%d WebTotal:%.0f%n",
				webResults.size(), webTotal);
		return new Pair<Integer, Double>(webResults.size(), webTotal);
	}

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
			url2jsonCache.put(url.toExternalForm(),
					(JSONObject) JSONValue.parse(resultStr));
			increaseFlushCounter();
		}

		if (recacheNeeded(url2jsonCache.get(url.toExternalForm()))
				&& retryLeft > 0)
			return queryBing(query, retryLeft - 1);

		return url2jsonCache.get(url.toExternalForm());
	}

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

	public static void unSetCache() {
		url2jsonCache = null;
		System.gc();
	}

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

	private HashMap<String, Double> generateEmptyQueryFeatures(
			int resultsCount, double webTotal,
			List<Pair<String, Integer>> bingBolds, List<String> urls,
			List<String> wikiSearchPages, double webTotalWiki) {
		SmaphAnnotatorDebugger.out
				.println("*** Generating EmptyQuery features ***");

		// Process URLS
		List<Integer> wikiPositions = new Vector<>();
		for (int i = 0; i < urls.size(); i++) {
			String url = urls.get(i);
			if (url.startsWith(WIKI_URL_LEADING))
				wikiPositions.add(i);
		}

		double wikiCount = wikiPositions.size();
		double wikiFreq = resultsCount == 0 ? 0 : ((double) wikiPositions
				.size()) / resultsCount;
		double avgWikiRank = RankWeightSpotFilter.computeAvgRank(wikiPositions,
				resultsCount);

		// Process Bing bolds
		double minFreq = 1;
		double maxFreq = 0;
		double avgFreq = 0;

		double minAvgRank = 1;
		double maxAvgRank = 0;
		double avgAvgRank = 0;

		HashSet<String> seenBolds = new HashSet<>();
		for (Pair<String, Integer> boldAndPos : bingBolds) {
			String bold = boldAndPos.first.toLowerCase();
			if (seenBolds.contains(bold))
				continue;
			seenBolds.add(bold);
			double freq = FrequencySpotFilter.getFrequency(bingBolds, bold,
					resultsCount);
			double avgRank = RankWeightSpotFilter.getAvgRank(bingBolds, bold,
					resultsCount);
			minFreq = Math.min(minFreq, freq);
			maxFreq = Math.max(maxFreq, freq);
			avgFreq += freq;
			minAvgRank = Math.min(minAvgRank, avgRank);
			maxAvgRank = Math.max(maxAvgRank, avgRank);
			avgAvgRank += avgRank;
		}

		if (!seenBolds.isEmpty()) {
			avgFreq /= seenBolds.size();
			avgAvgRank /= seenBolds.size();
		} else {
			avgFreq = 0.5f;
			avgAvgRank = 0.5f;
		}

		HashMap<String, Double> res = new HashMap<>();
		res.put("webTotal", (double) webTotal);
		res.put("minAvgRank", minAvgRank);
		res.put("maxAvgRank", maxAvgRank);
		res.put("avgAvgRank", avgAvgRank);
		res.put("minFreq", minFreq);
		res.put("maxFreq", maxFreq);
		res.put("avgFreq", avgFreq);
		res.put("avgWikiRank", avgWikiRank);
		res.put("wikiFreq", wikiFreq);
		res.put("wikiCount", wikiCount);
		res.put("webTotalWiki", (double) webTotalWiki);

		for (String key : res.keySet())
			SmaphAnnotatorDebugger.out.printf("%s: %f", key, res.get(key));

		return res;
	}

	private HashMap<String, Double> generateEntitySelectionFeaturesAnnotation(
			String query, int resultsCount, Annotation ann,
			Pair<String, HashSet<Mention>> annInput,
			List<Pair<String, Integer>> bingBolds,
			HashMap<String, HashMap<String, Double>> additionalInfo) {
		HashMap<String, Double> result = new HashMap<>();

		String bold = annInput.first.substring(ann.getPosition(),
				ann.getPosition() + ann.getLength());
		result.put("is_s1", 1.0);
		result.put("s1_freq",
				FrequencySpotFilter.getFrequency(bingBolds, bold, resultsCount));
		result.put("s1_avgRank",
				RankWeightSpotFilter.getAvgRank(bingBolds, bold, resultsCount));

		result.put("s1_editDistance", SmaphUtils.getMinEditDist(query, bold));

		// Add additional info like rho, commonness, etc.
		for (String key : additionalInfo.get(bold).keySet())
			result.put("s1_" + key, additionalInfo.get(bold).get(key));

		return result;

	}

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

	private HashMap<String, Double> generateEntitySelectionFeaturesAnnotationCandidates(
			String query, String mention, HashMap<String, Double> candidateData) {
		HashMap<String, Double> result = new HashMap<>();
		result.put("is_s4", 1.0);
		try {
			result.put("s4_editDistance", SmaphUtils.getMinEditDist(query,
					wikiApi.getTitlebyId(candidateData.get("id").intValue())));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// Add additional info like rho, commonness, etc.
		for (String key : candidateData.keySet())
			result.put("s4_" + key, candidateData.get(key));

		return result;
	}

	public void generateExamples(String query, HashSet<Tag> goldStandard,
			Vector<double[]> posEFVectors, Vector<double[]> negEFVectors,
			Vector<double[]> posEQFVectors, Vector<double[]> negEQFVectors,
			boolean discardNE, WikipediaToFreebase wikiToFreeb) throws Exception {

		/** Search the query on bing */
		List<Pair<String, Integer>> bingBoldsAndRank = null;
		List<String> urls = null;
		List<String> relatedSearchRes = null;
		Pair<Integer, Double> resCountAndWebTotal = null;
		int resultsCount = -1;
		double webTotal = Double.NaN;
		List<String> filteredBolds = null;
		if (includeSourceAnnotator || includeSourceAnnotatorTopK
				|| includeSourceWikiSearch || includeSourceRelatedSearch
				|| includeSourceNormalSearch) {
			bingBoldsAndRank = new Vector<>();
			urls = new Vector<>();
			relatedSearchRes = new Vector<>();
			resCountAndWebTotal = takeBingData(query, bingBoldsAndRank, urls,
					relatedSearchRes);
			resultsCount = resCountAndWebTotal.first;
			webTotal = resCountAndWebTotal.second;
			filteredBolds = boldFilter.filterBolds(query, bingBoldsAndRank,
					resultsCount);
		}

		/** Do the wikipedia-search on bing. */
		List<String> wikiSearchUrls = new Vector<>();
		List<Pair<String, Integer>> bingBoldsAndRankWS = new Vector<>();
		HashMap<String, Pair<Integer, Integer>> annTitlesToIdAndRankWS = null;
		double webTotalWiki = Double.NaN;
		if (includeSourceWikiSearch | includeSourceNormalSearch) {
			webTotalWiki = takeBingWikiResults(query, wikiSearchUrls,
					bingBoldsAndRankWS, topKWikiSearch);
			HashMap<Integer, Integer> rankToIdWikiSearch = urlsToRankID(wikiSearchUrls);
			annTitlesToIdAndRankWS = getTitlesForAnnotator(rankToIdWikiSearch);
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
			webTotalRelatedSearch = takeBingWikiResults(relatedSearch,
					relatedSearchUrls, bingBoldsAndRankRS, topKRelatedSearch);
			rankToIdRelatedSearch = urlsToRankID(relatedSearchUrls);
			annTitlesToIdAndRankRS = getTitlesForAnnotator(rankToIdRelatedSearch);
		}

		/** Annotate bolds on the annotator */
		Triple<HashMap<String, HashMap<String, Double>>, HashMap<String, Annotation>, HashMap<String, List<HashMap<String, Double>>>> infoAndAnnotations = null;
		HashMap<String, Annotation> spotToAnnotation = null;
		HashMap<String, HashMap<String, Double>> additionalInfo = null;
		HashMap<String, List<HashMap<String, Double>>> candidatesInfo = null;
		Pair<String, HashSet<Mention>> annInput = null;
		if (includeSourceAnnotator || includeSourceAnnotatorTopK) {
			annInput = generateAnnotatorInput(filteredBolds, null);
			infoAndAnnotations = solveD2WDisambiguator(annInput.first,
					annInput.second);
			spotToAnnotation = infoAndAnnotations.getMiddle();
			additionalInfo = infoAndAnnotations.getLeft();
			candidatesInfo = infoAndAnnotations.getRight();
		}

		List<Pair<Tag, HashMap<String, Double>>> widToEFFtrVect = new Vector<>();
		// Filter and add annotations found by the disambiguator
		if (includeSourceAnnotator) {
			for (String bold : filteredBolds) {
				if (spotToAnnotation.containsKey(bold)) {
					Annotation ann = spotToAnnotation.get(bold);
					HashMap<String, Double> ESFeatures = generateEntitySelectionFeaturesAnnotation(
							query, resultsCount, ann, annInput,
							bingBoldsAndRank, additionalInfo);
					Tag tag = new Tag(ann.getConcept());
					widToEFFtrVect.add(new Pair<Tag, HashMap<String, Double>>(
							tag, ESFeatures));
				}
			}
		}

		// Filter and add entities found by the disambiguator, TOP-K
		if (includeSourceAnnotatorTopK) {
			for (String mention : candidatesInfo.keySet())
				for (HashMap<String, Double> candidateData : candidatesInfo
						.get(mention)) {
					HashMap<String, Double> ESFeatures = generateEntitySelectionFeaturesAnnotationCandidates(
							query, mention, candidateData);
					Tag tag = new Tag(candidateData.get("id").intValue());
					widToEFFtrVect.add(new Pair<Tag, HashMap<String, Double>>(
							tag, ESFeatures));
				}
		}

		// Filter and add entities found in the normal search
		if (includeSourceNormalSearch) {
			HashMap<Integer, Integer> rankToId = urlsToRankID(urls);
			for (int rank : rankToId.keySet()) {
				int wid = rankToId.get(rank);
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

		// Add EQF features.
		{
			HashMap<String, Double> featuresEmptyQuery = generateEmptyQueryFeatures(
					resultsCount, webTotal, bingBoldsAndRank, urls,
					wikiSearchUrls, webTotalWiki);

			if (!goldStandard.isEmpty())
				posEQFVectors.add(LibSvmEmptyQueryFilter
						.featuresToFtrVectStatic(featuresEmptyQuery));
			else
				negEQFVectors.add(LibSvmEmptyQueryFilter
						.featuresToFtrVectStatic(featuresEmptyQuery));
			System.out
					.printf("query [%s] annotations=%.0f webTotal=%.0f minAvgRank=%.3f maxFreq=%.3f is a %s example.%n",
							query, featuresEmptyQuery.get("annotations"),
							featuresEmptyQuery.get("webTotal"),
							featuresEmptyQuery.get("minAvgRank"),
							featuresEmptyQuery.get("maxFreq"),
							!goldStandard.isEmpty() ? "positive" : "negative");
		}
	}

}
