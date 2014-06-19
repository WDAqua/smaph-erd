package it.acubelab.erd;

import it.acubelab.batframework.utils.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import org.apache.commons.lang.StringUtils;
import org.tartarus.snowball.ext.EnglishStemmer;

public class SmaphUtils {

	public static double getMinEditDist(String query, String bold) {
		query = query.replaceAll("\\W+", " ").toLowerCase();
		bold = bold.replaceAll("\\W+", " ").toLowerCase();

		String[] tokensQ = query.split("\\s+");
		String[] tokensB = bold.split("\\s+");

		if (tokensB.length == 0 || tokensQ.length == 0)
			return 1;

		float avgMinDist = 0;
		for (String tokenB : tokensB) {
			float minDist = 1;
			for (String tokenQ : tokensQ) {
				float relLev = getNormEditDistance(tokenB, tokenQ);
				if (relLev < minDist)
					minDist = relLev;
			}
			avgMinDist += minDist;
		}
		return avgMinDist / tokensB.length;
	}

	public static float getNormEditDistance(String tokenB, String tokenQ) {
		if (tokenQ.isEmpty() || tokenB.isEmpty())
			return 1;
		int lev = StringUtils.getLevenshteinDistance(tokenB, tokenQ);
		return (float) lev / (float) Math.max(tokenB.length(), tokenQ.length());
	}

	public static boolean acceptWikipediaTitle(String title) {
		// TODO: this can definitely be done in a cleaner way.
		return !(title.startsWith("Talk:") || title.startsWith("Special:")
				|| title.startsWith("Portal:")
				|| title.startsWith("Wikipedia:")
				|| title.startsWith("Wikipedia_talk:")
				|| title.startsWith("File:") || title.startsWith("User:")
				|| title.startsWith("Category:") || title.startsWith("List") || title
					.contains("(disambiguation)"));
	}

	public static Vector<Integer> getAllFtrVect(int ftrCount) {
		Vector<Integer> res = new Vector<>();
		for (int i = 1; i < ftrCount + 1; i++)
			res.add(i);
		return res;
	}

	public static void mapRankToBoldsLC(
			List<Pair<String, Integer>> spotAndRanks,
			HashMap<Integer, HashSet<String>> positions, HashSet<String> spots) {

		for (Pair<String, Integer> spotAndRank : spotAndRanks) {
			String spot = spotAndRank.first.toLowerCase();
			int rank = spotAndRank.second;
			spots.add(spot);
			if (!positions.containsKey(rank))
				positions.put(rank, new HashSet<String>());
			positions.get(rank).add(spot);
		}

	}

	public static HashMap<String, HashSet<Integer>> findPositionsLC(
			List<Pair<String, Integer>> boldAndRanks) {
		HashMap<String, HashSet<Integer>> positions = new HashMap<>();
		for (Pair<String, Integer> boldAndRank : boldAndRanks) {
			String spot = boldAndRank.first.toLowerCase();
			int rank = boldAndRank.second;
			if (!positions.containsKey(spot))
				positions.put(spot, new HashSet<Integer>());
			positions.get(spot).add(rank);
		}
		return positions;
	}

	public static String stemString(String str, EnglishStemmer stemmer) {
		String stemmedString = "";
		String[] words = str.split("\\s+");
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			stemmer.setCurrent(word);
			stemmer.stem();
			stemmedString += stemmer.getCurrent();
			if (i != words.length)
				stemmedString += " ";
		}
		return stemmedString;
	}

}
