/**
 *  Copyright 2014 Marco Cornolti
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package it.acubelab.smaph.entityfilters;

import it.acubelab.smaph.SmaphAnnotatorDebugger;
import it.acubelab.smaph.learn.LibSvmFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Vector;

import org.apache.commons.lang.ArrayUtils;

/**
 * An SVM-based entity filter.
 */
public class LibSvmEntityFilter extends LibSvmFilter implements EntityFilter {

	public static String[] ftrNames = new String[] {
			"is_s1", // 1
			"is_s2",
			"is_s3",
			"is_s4",
			"is_s5",
			"s1_freq",
			"s1_rhoScore", //
			"s1_localCoherence", //
			"s1_lp",
			"s1_editDistance", // 10
			"s1_commonness", //
			"s1_avgRank",
			"s1_ambiguity",
			"s1_pageRank", //
			"s2_editDistance", "s2_rank",
			"s2_webTotalWiki",
			"s2_webTotal",
			"s3_rank",
			"s3_wikiWebTotal", // 20
			"s3_editDistanceTitle", "s3_editDistanceNoPar",
			"s3_editDistanceBolds", "s3_capitalizedBolds", "s3_avgBoldsWords",
			"s5_rank", "s5_wikiWebTotal", "s5_editDistanceTitle",
			"s5_editDistanceNoPar", "s5_editDistanceBolds", // 30
			"s5_capitalizedBolds", "s5_avgBoldsWords",

	};

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

	/**
	 * Turns a frature_name-feature_value mapping to an array of features.
	 * 
	 * @param features
	 *            the mapping from feature names to feature values.
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

		Vector<Double> ftrValues = new Vector<>();
		for (String ftrName : ftrNames)
			ftrValues.add(getOrDefault(features, ftrName, 0.0));
		
		return ArrayUtils.toPrimitive(ftrValues.toArray(new Double[] {}));
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
