package it.acubelab.erd.boldfilters;

import it.acubelab.batframework.utils.Pair;
import it.acubelab.erd.SmaphUtils;

import java.util.*;

/**
 * A filter that filters out all bolds that have an edit distance higher than the threshold.
 */
public class RankWeightBoldFilter implements BoldFilter {
	private float maxSpotScore;

	public RankWeightBoldFilter(float maxSpotScore) {
		this.maxSpotScore = maxSpotScore;
	}

	@Override
	public List<String> filterBolds(String query,
			List<Pair<String, Integer>> spotAndRanks, int resultsCount) {
		HashMap<Integer, HashSet<String>> positions = new HashMap<>();
		HashSet<String> spots = new HashSet<>();
		SmaphUtils.mapRankToBoldsLC(spotAndRanks, positions, spots);

		List<String> result = new Vector<>();
		for (String spot : spots) {
			double avg = getAvgRank(resultsCount, positions, spot);
			if (avg <= this.maxSpotScore)
				result.add(spot);
		}
		return result;
	}

	private static double getAvgRank(int resultsCount,
			HashMap<Integer, HashSet<String>> rankToBolds, String spot) {
		List<Integer> positions = new Vector<>();
		for (int rank = 0; rank < resultsCount; rank++)
			if (rankToBolds.containsKey(rank)
					&& rankToBolds.get(rank).contains(spot))
				positions.add(rank);
		return computeAvgRank(positions, resultsCount);
	}

	public static double getAvgRank(List<Pair<String, Integer>> spotAndRanks,
			String spot, int resultsCount) {
		HashMap<Integer, HashSet<String>> positions = new HashMap<>();
		HashSet<String> spots = new HashSet<>();
		SmaphUtils.mapRankToBoldsLC(spotAndRanks, positions, spots);
		return getAvgRank(resultsCount, positions, spot);
	}

	public static double computeAvgRank(List<Integer> positions, int resultsCount) {
		if (resultsCount==0) return 1;
		float avg = 0;
		for (int pos : positions)
			avg += (float)pos/(float)resultsCount;
		avg += resultsCount - positions.size();
		
		avg /= resultsCount;
		return avg;
	}
}
