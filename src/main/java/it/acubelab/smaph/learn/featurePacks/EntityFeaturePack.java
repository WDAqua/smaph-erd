package it.acubelab.smaph.learn.featurePacks;

import java.util.HashMap;

public class EntityFeaturePack extends FeaturePack {
	private static final long serialVersionUID = 1L;

	public EntityFeaturePack(HashMap<String, Double> ftrs) {
		super(ftrs);
	}

	public EntityFeaturePack() {
		super(null);
	}

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
			"s2_editDistanceTitle",
			"s2_rank",
			"s2_wikiWebTotal",
			"s2_webTotal",
			"s3_rank",
			"s3_wikiWebTotal", // 20
			"s3_editDistanceTitle", "s3_editDistanceNoPar",
			"s3_editDistanceBolds", "s3_capitalizedBolds", "s3_avgBoldsWords",
			"s5_rank", "s5_wikiWebTotal",
			"s5_editDistanceTitle",
			"s5_editDistanceNoPar",
			"s5_editDistanceBolds", // 30
			"s5_capitalizedBolds", "s5_avgBoldsWords", "s3_webTotal",
			"s2_editDistanceNoPar", "s2_editDistanceBolds",
			"s2_capitalizedBolds", "s2_avgBoldsWords", // 37
			"is_named_entity"
	};

	@Override
	public String[] getFeatureNames() {
		return getFeatureNamesStatic();
	}

	private static String[] getFeatureNamesStatic() {
		return ftrNames;
	}

	@Override
	public void checkFeatures(HashMap<String, Double> features) {
		int sourceCount = 0;
		for (String ftrName : new String[] { "is_s1", "is_s2", "is_s3",
				"is_s4", "is_s5" }) {
			if (!features.containsKey(ftrName))
				throw new RuntimeException(
						"All entity sources must be set (one source to 1.0, all others to 0.0)");
			sourceCount += features.get(ftrName);
		}

		if (sourceCount != 1.0)
			throw new RuntimeException(
					"All sources must be set to 0.0, except from one source that must be set to 1.0");

		boolean found = false;
		for (String sourcePrefix : new String[] { "s1_", "s2_", "s3_", "s5_" }) {
			int sourceFtrCount = 0;

			for (String ftrName : features.keySet())
				if (ftrName.startsWith(sourcePrefix))
					sourceFtrCount++;
			int baseFeatures = 6;
			if (sourcePrefix.equals("s1_"))
				found = sourceFtrCount == 9
						&& features.size() == sourceFtrCount + baseFeatures;
			if (sourcePrefix.equals("s2_"))
				found = sourceFtrCount == 8
						&& features.size() == sourceFtrCount + baseFeatures;
			if (sourcePrefix.equals("s3_"))
				found = sourceFtrCount == 8
						&& features.size() == sourceFtrCount + baseFeatures;
			if (sourcePrefix.equals("s4_"))
				found = sourceFtrCount == 0
						&& features.size() == sourceFtrCount + baseFeatures;
			if (sourcePrefix.equals("s5_"))
				found = sourceFtrCount == 8
						&& features.size() == sourceFtrCount + baseFeatures;

			if (found)
				return;
		}
		throw new RuntimeException("Incorrect number of features.");
	}

}
