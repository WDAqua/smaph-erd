package it.acubelab.erd.boldfilters;

import it.acubelab.batframework.utils.Pair;

import java.util.List;

/**
 * An interface to a bold filter.
 */
public interface BoldFilter {
	/**
	 * @param query the query.
	 * @param spotAndRank a list of pairs &lt;b,r&gt;, meaning bold b appeared in result ranked r.
	 * @param resultsCount the number of results returned by the search engine.
	 * @return the list of bolds that should be kept.
	 * 	 */
	public List<String> filterBolds(String query, List<Pair<String, Integer>> spotAndRank, int resultsCount);
}
