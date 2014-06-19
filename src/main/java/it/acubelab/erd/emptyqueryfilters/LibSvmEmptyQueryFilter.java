package it.acubelab.erd.emptyqueryfilters;

import it.acubelab.erd.SmaphAnnotatorDebugger;
import it.acubelab.erd.learn.LibSvmFilter;

import java.io.IOException;
import java.util.HashMap;

public class LibSvmEmptyQueryFilter extends LibSvmFilter implements
		EmptyQueryFilter {

	public LibSvmEmptyQueryFilter(String modelFileBase) throws IOException {
		super(modelFileBase + ".model", modelFileBase + ".range");
	}

	@Override
	public boolean filterQuery(HashMap<String, Double> queryFtrs) {
		boolean prediction = predict(queryFtrs);
		SmaphAnnotatorDebugger.out.printf("LibSvm EQ Filter: %s%n",
				prediction ? "query is non-empty" : "query is empty");
		return prediction;
	}

	public static double[] featuresToFtrVectStatic(
			HashMap<String, Double> features) {
		return new double[] { features.get("minFreq"), features.get("maxFreq"),
				features.get("avgFreq"), features.get("minAvgRank"),
				features.get("maxAvgRank"), features.get("avgAvgRank"),
				features.get("webTotal"), features.get("avgWikiRank"),
				features.get("wikiFreq"), features.get("wikiCount"),
				features.get("webTotalWiki") };
	}

	@Override
	public double[] featuresToFtrVect(HashMap<String, Double> features) {
		return featuresToFtrVectStatic(features);
	}

}
