package it.acubelab.smaph.linkback;

import it.acubelab.smaph.entityfilters.LibSvmEntityFilter;
import it.acubelab.smaph.learn.LibSvmModel;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

public class SvmBindingRegressor extends LibSvmModel {
	public SvmBindingRegressor(String modelFileBase) throws IOException {
		super(modelFileBase + ".model", modelFileBase + ".range");
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

		String[] ftrNames = getFeatureNames();
		double[] featureVect = new double[ftrNames.length];
		int i = 0;
		for (String ftrName : ftrNames)
			featureVect[i++] = getOrDefault(features, ftrName, 0.0);

		return featureVect;
	}

	public static boolean checkFeatures(HashMap<String, Double> features) {
		String[] ftrNames = getFeatureNames();
		for (String ftrName: features.keySet())
			if (!Arrays.asList(ftrNames).contains(ftrName))
				return false;
		return true;
	}

	public static String[] getFeatureNames() {
		List<String> ftrNames = new Vector<>();
		ftrNames.add("min_min_edit_distance");
		ftrNames.add("avg_min_edit_distance");
		ftrNames.add("max_min_edit_distance");
		
		for (String ftrName:LibSvmEntityFilter.ftrNames)
			if (ftrName.startsWith("s1_")||ftrName.startsWith("s2_")||ftrName.startsWith("s3_")){
				ftrNames.add("min_"+ftrName);
				ftrNames.add("max_"+ftrName);
				ftrNames.add("avg_"+ftrName);
			}
		for (String ftrName:LibSvmEntityFilter.ftrNames)
			if (ftrName.startsWith("is_")){
				ftrNames.add("count_"+ftrName);
			}

		return ftrNames.toArray(new String[]{});
	}

	@Override
	public double[] featuresToFtrVect(HashMap<String, Double> features) {
		return featuresToFtrVectStatic(features);
	}

}
