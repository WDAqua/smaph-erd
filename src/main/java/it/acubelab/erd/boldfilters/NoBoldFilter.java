package it.acubelab.erd.boldfilters;

import it.acubelab.batframework.utils.Pair;

import java.util.*;

/**
 * A filter that does nothing (accept all bolds).
 */
public class NoBoldFilter implements BoldFilter {

	@Override
	public List<String> filterBolds(String query,
			List<Pair<String, Integer>> spotAndRanks, int resultsCount) {
		HashSet<String> filteredSpots = new HashSet<>();
		for (Pair<String, Integer> spotAndRank : spotAndRanks)
			filteredSpots.add(spotAndRank.first);
		return new ArrayList<String>(filteredSpots);
	}

}
