package it.acubelab.erd.spotfilters;

import it.acubelab.batframework.utils.Pair;
import it.acubelab.erd.SmaphAnnotatorDebugger;
import it.acubelab.erd.SmaphUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

public class FrequencySpotFilter implements SpotFilter {
	private float minSpotFreq;

	public FrequencySpotFilter(float minSpotFreq) {
		this.minSpotFreq = minSpotFreq;
	}

	@Override
	public List<String> filterBolds(String query,
			List<Pair<String, Integer>> boldAndRanks, int resultsCount) {
		HashMap<String, HashSet<Integer>> positions = SmaphUtils.findPositionsLC(boldAndRanks);

		List<String> spots = new Vector<>();
		for (String spot : positions.keySet())
			if (getFrequency(positions.get(spot).size(), resultsCount) >= this.minSpotFreq) {
				spots.add(spot);
				SmaphAnnotatorDebugger.out.printf("%s -> %d%n", spot, positions.get(spot)
						.size());
			}
		return spots;
	}

	public static double getFrequency(int occurrences, int resultsCount) {
		return (float) occurrences / (float) resultsCount;
	}

	public static double getFrequency(List<Pair<String, Integer>> boldAndRanks,
			String bold, int resultsCount) {
		HashMap<String, HashSet<Integer>> positions = SmaphUtils.findPositionsLC(boldAndRanks);
		return getFrequency(positions.get(bold.toLowerCase()).size(), resultsCount);
	}
}
