package it.acubelab.smaph.linkback;

import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.data.Mention;
import it.acubelab.batframework.data.ScoredAnnotation;
import it.acubelab.batframework.data.Tag;
import it.acubelab.batframework.utils.Pair;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.smaph.QueryInformation;
import it.acubelab.smaph.SmaphUtils;
import it.acubelab.smaph.learn.featurePacks.BindingFeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;
import it.acubelab.smaph.linkback.annotationRegressor.AnnotationRegressor;
import it.acubelab.smaph.linkback.bindingGenerator.BindingGenerator;
import it.acubelab.smaph.linkback.bindingRegressor.BindingRegressor;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import org.tartarus.snowball.ext.EnglishStemmer;

public class SvmCollectiveLinkBack implements LinkBack {
	private BindingRegressor bindingRegressorModel;
	private WikipediaApiInterface wikiApi;
	private Vector<Double> predictionScoresDebug = new Vector<>();
	private BindingGenerator bg;
	private FeatureNormalizer fn;
	private AnnotationRegressor ar;

	public SvmCollectiveLinkBack(WikipediaApiInterface wikiApi,
			BindingGenerator bg, AnnotationRegressor ar, BindingRegressor lbReg, FeatureNormalizer fn) throws IOException {
		this.bindingRegressorModel = lbReg;
		this.wikiApi = wikiApi;
		this.bg = bg;
		this.ar = ar;
		this.fn = fn;
	}

	private boolean segmentsOverlap(Pair<Integer, Integer> s1,
			Pair<Integer, Integer> s2) {
		return new Mention(s1.first, s1.second - s1.first)
				.overlaps(new Mention(s2.first, s2.second - s2.first));
	}

	@Override
	public HashSet<ScoredAnnotation> linkBack(String query,
			HashSet<Tag> acceptedEntities, QueryInformation qi) {
		HashMap<Tag, String[]> entitiesToBoldsS1 = SmaphUtils
				.getEntitiesToBolds(qi.boldToEntityS1, acceptedEntities);
		HashMap<Tag, String> entitiesToTitles = SmaphUtils.getEntitiesToTitles(
				acceptedEntities, wikiApi);
		
		// Generate all possible bindings
		List<HashSet<Annotation>> bindings = bg.getBindings(query, qi,
				acceptedEntities, wikiApi);

		// Precompute annotation regressor scores
		HashMap<Annotation, Double> regressorScores = null;
		if (ar != null)
			regressorScores = SmaphUtils.predictBestScores(ar, fn, bindings, query,
					qi.entityToFtrVects, entitiesToBoldsS1, entitiesToTitles,
					new EnglishStemmer());
		

		// Predict a score and pick the best-performing
		HashSet<Annotation> bestBinding = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (HashSet<Annotation> binding : bindings) {
			BindingFeaturePack features = new BindingFeaturePack(binding, query,
					entitiesToBoldsS1, entitiesToTitles, qi.entityToFtrVects,
					regressorScores);
			double predictedScore = bindingRegressorModel
					.predictScore(features,fn);
			predictionScoresDebug.add(predictedScore);
			if (predictedScore > bestScore) {
				bestBinding = binding;
				bestScore = predictedScore;
			}
		}

		HashSet<ScoredAnnotation> scoredBestBinding = new HashSet<>();
		for (Annotation ann : bestBinding) {
			scoredBestBinding.add(new ScoredAnnotation(ann.getPosition(), ann
					.getLength(), ann.getConcept(), 1.0f));
		}
		return scoredBestBinding;
	}


	public Vector<Double> getPredictionScores() {
		return predictionScoresDebug;

	}
}
