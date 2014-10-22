package it.acubelab.smaph.linkback.annotationRegressor;

import it.acubelab.smaph.learn.featurePacks.FeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;

public interface AnnotationRegressor {
	public double predictScore(FeaturePack fp, FeatureNormalizer fn);	
}
