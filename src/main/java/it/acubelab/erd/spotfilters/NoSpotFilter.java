package it.acubelab.erd.spotfilters;

import it.acubelab.batframework.utils.Pair;

import java.util.*;

public class NoSpotFilter implements SpotFilter {

	@Override
	public List<String> filterBolds(String query,
			List<Pair<String, Integer>> spotAndRanks, int resultsCount) {
		HashSet<String> filteredSpots = new HashSet<>();
		for (Pair<String, Integer> spotAndRank : spotAndRanks)
			filteredSpots.add(spotAndRank.first);
		return new ArrayList<String>(filteredSpots);
	}

}
