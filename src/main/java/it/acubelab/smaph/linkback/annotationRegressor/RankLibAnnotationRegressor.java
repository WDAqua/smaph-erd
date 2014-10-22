package it.acubelab.smaph.linkback.annotationRegressor;

import it.acubelab.smaph.learn.RankLibRanker;

public class RankLibAnnotationRegressor extends RankLibRanker implements AnnotationRegressor{
	public RankLibAnnotationRegressor(String modelFile) {
		super(modelFile);
	}
}