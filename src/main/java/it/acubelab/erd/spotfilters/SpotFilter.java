package it.acubelab.erd.spotfilters;

import it.acubelab.batframework.utils.Pair;

import java.util.List;

public interface SpotFilter {
	public List<String> filterBolds(String query, List<Pair<String, Integer>> spotAndRank, int resultsCount);
}
