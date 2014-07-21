package it.acubelab.erd.boldfilters;

import it.acubelab.batframework.utils.Pair;
import it.acubelab.erd.SmaphAnnotatorDebugger;
import it.acubelab.erd.SmaphUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Vector;

/**
 * A filter that filters out all bolds that have an edit distance higher than the threshold.
 */
public class EditDistanceBoldFilter implements BoldFilter {
	private double threshold;

	public EditDistanceBoldFilter(double threshold) {
		this.threshold = threshold;
	}

	@Override
	public List<String> filterBolds(String query,
			List<Pair<String, Integer>> spotAndRanks, int resultsCount) {
		List<String> res = new Vector<>();
		HashSet<String> seen = new HashSet<>();
		SmaphAnnotatorDebugger.out.println("*** Filtering Bolds ***");
		for (Pair<String, Integer> spotAndRank : spotAndRanks) {
			String bold = spotAndRank.first.toLowerCase();
			if (seen.contains(bold))
				continue;
			seen.add(bold);
			double minDist = SmaphUtils
					.getMinEditDist(query, bold);
			boolean accept = minDist < threshold;
			if (accept)
				res.add(bold);
			SmaphAnnotatorDebugger.out.printf("Min edit distance: %f (%s)%n",
					minDist, accept ? "accept" : "discard");

		}
		return res;
	}

}
