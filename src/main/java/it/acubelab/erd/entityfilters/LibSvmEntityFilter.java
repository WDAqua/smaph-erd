package it.acubelab.erd.entityfilters;

import it.acubelab.erd.SmaphAnnotatorDebugger;
import it.acubelab.erd.learn.LibSvmFilter;

import java.io.*;
import java.nio.channels.Pipe.SourceChannel;
import java.util.HashMap;

/**
 * An SVM-based entity filter.
 */
public class LibSvmEntityFilter extends LibSvmFilter implements EntityFilter {

	public LibSvmEntityFilter(String modelFileBase) throws IOException {
		super(modelFileBase + ".model", modelFileBase + ".range");
	}

	@Override
	public boolean filterEntity(HashMap<String, Double> features) {
		boolean result = predict(features);
		String ftrDesc = "";
		for (String key : features.keySet())
			ftrDesc += String.format("%s:%.3f ", key, features.get(key));
		SmaphAnnotatorDebugger.out.printf("EF: %s has been %s.%n", ftrDesc,
				result ? "accepted" : "discarded");
		return result;
	}

	private static double getOrDefault(HashMap<String, Double> features,
			String key, double defaultVal) {
		Double res = features.get(key);
		if (res == null)
			return defaultVal;
		return res;
	}

	/**Turns a frature_name-feature_value mapping to an array of features.
	 * @param features the mapping from feature names to feature values.
	 * @return an array of feature values.
	 */
	public static double[] featuresToFtrVectStatic(
			HashMap<String, Double> features) {

		if (!checkFeatures(features)) {
			for (String ftrName : features.keySet())
				System.err.printf("%s -> %f%n", ftrName, features.get(ftrName));
			throw new RuntimeException(
					"Implementation error -- check the features");
		}

		return new double[] {
				getOrDefault(features, "is_s1", 0.0), 							// 1
				getOrDefault(features, "is_s2", 0.0),
				getOrDefault(features, "is_s3", 0.0),
				getOrDefault(features, "is_s4", 0.0),
				getOrDefault(features, "is_s5", 0.0),
				getOrDefault(features, "s1_freq", 0.0),
				getOrDefault(features, "s1_rhoScore", 0.0),//
				getOrDefault(features, "s1_localCoherence", 0.0),//
				getOrDefault(features, "s1_lp", 0.0),
				getOrDefault(features, "s1_editDistance", 0.0), 				// 10
				getOrDefault(features, "s1_commonness", 0.0),//
				getOrDefault(features, "s1_avgRank", 0.0),
				getOrDefault(features, "s1_ambiguity", 0.0),
				getOrDefault(features, "s1_pageRank", 0.0),//
				getOrDefault(features, "s2_editDistance", 0.0),
				getOrDefault(features, "s2_rank", 0.0),
				getOrDefault(features, "s2_webTotalWiki", 0.0),
				getOrDefault(features, "s2_webTotal", 0.0),
				getOrDefault(features, "s3_rank", 0.0),
				getOrDefault(features, "s3_wikiWebTotal", 0.0), 				// 20
				getOrDefault(features, "s3_editDistanceTitle", 0.0),
				getOrDefault(features, "s3_editDistanceNoPar", 0.0),
				getOrDefault(features, "s3_editDistanceBolds", 0.0),
				getOrDefault(features, "s3_capitalizedBolds", 0.0),
				getOrDefault(features, "s3_avgBoldsWords", 0.0),
				getOrDefault(features, "s5_rank", 0.0),
				getOrDefault(features, "s5_wikiWebTotal", 0.0),
				getOrDefault(features, "s5_editDistanceTitle", 0.0),
				getOrDefault(features, "s5_editDistanceNoPar", 0.0),
				getOrDefault(features, "s5_editDistanceBolds", 0.0),			// 30
				getOrDefault(features, "s5_capitalizedBolds", 0.0),
				getOrDefault(features, "s5_avgBoldsWords", 0.0),
		};
	}

	private static boolean checkFeatures(HashMap<String, Double> features) {
		if (getOrDefault(features, "is_s1", 0.0)
				+ getOrDefault(features, "is_s2", 0.0)
				+ getOrDefault(features, "is_s3", 0.0)
				+ getOrDefault(features, "is_s4", 0.0)
				+ getOrDefault(features, "is_s5", 0.0) != 1)
			return false;
		boolean found = false;
		for (String sourcePrefix : new String[] { "s1_", "s2_", "s3_", "s5_" }) {
			int sourceFtrCount = 0;

			for (String ftrName : features.keySet())
				if (ftrName.startsWith(sourcePrefix))
					sourceFtrCount++;

			if (sourcePrefix.equals("s1_"))
				found = sourceFtrCount == 9
						&& features.size() == sourceFtrCount + 1;
			if (sourcePrefix.equals("s2_"))
				found = sourceFtrCount == 4
						&& features.size() == sourceFtrCount + 1;
			if (sourcePrefix.equals("s3_"))
				found = sourceFtrCount == 7
						&& features.size() == sourceFtrCount + 1;
			if (sourcePrefix.equals("s4_"))
				found = sourceFtrCount == 0
						&& features.size() == sourceFtrCount + 1;
			if (sourcePrefix.equals("s5_"))
				found = sourceFtrCount == 7
						&& features.size() == sourceFtrCount + 1;

			if (found)
				return true;
		}
		return false;
	}

	@Override
	public double[] featuresToFtrVect(HashMap<String, Double> features) {
		return featuresToFtrVectStatic(features);
	}
}
