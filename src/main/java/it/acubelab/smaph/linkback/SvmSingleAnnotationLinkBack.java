package it.acubelab.smaph.linkback;

import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.data.ScoredAnnotation;
import it.acubelab.batframework.data.Tag;
import it.acubelab.batframework.utils.Pair;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.smaph.QueryInformation;
import it.acubelab.smaph.SmaphUtils;
import it.acubelab.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;
import it.acubelab.smaph.linkback.annotationRegressor.AnnotationRegressor;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.tartarus.snowball.ext.EnglishStemmer;

public class SvmSingleAnnotationLinkBack implements LinkBack {
	private AnnotationRegressor ar;
	private FeatureNormalizer annFn;
	private WikipediaApiInterface wikiApi;
	private double threshold;

	public SvmSingleAnnotationLinkBack(AnnotationRegressor ar,
			FeatureNormalizer annFn, WikipediaApiInterface wikiApi,
			double threshold) {
		this.ar = ar;
		this.annFn = annFn;
		this.wikiApi = wikiApi;
		this.threshold = threshold;
	}

	public static List<Annotation> getAnnotations(String query,
			Set<Tag> acceptedEntities, QueryInformation qi){
		List<Pair<Integer, Integer>> segments = SmaphUtils.findSegments(query);
		List<Annotation> annotations = new Vector<>();
		for (Tag t : acceptedEntities)
			for (Pair<Integer, Integer> segment : segments) 
				annotations.add(new Annotation(segment.first, segment.second-segment.first, t.getConcept()));
				
		return annotations;
	}
	
	@Override
	public HashSet<ScoredAnnotation> linkBack(String query,
			HashSet<Tag> acceptedEntities, QueryInformation qi) {

		HashMap<Tag, String> entityToTitle = SmaphUtils.getEntitiesToTitles(
				acceptedEntities, wikiApi);
		HashMap<Tag, String[]> entityToBoldsS1 = SmaphUtils.getEntitiesToBolds(
				qi.boldToEntityS1, acceptedEntities);

		EnglishStemmer stemmer = new EnglishStemmer();

		List<Pair<Double, Annotation>> scoreAndAnnotations = new Vector<>();
		for (Annotation a : getAnnotations(query, acceptedEntities, qi)) {
			double bestScore = Double.NEGATIVE_INFINITY;
			for (HashMap<String, Double> entityFeatures : qi.entityToFtrVects
					.get(new Tag(a.getConcept()))) {
				double score = ar.predictScore(new AnnotationFeaturePack(a, query, stemmer,
						entityFeatures, entityToBoldsS1, entityToTitle), annFn);
				if (score > bestScore)
					bestScore = score;
			}
			scoreAndAnnotations.add(new Pair<Double, Annotation>(bestScore, a));
		}

		Collections.sort(scoreAndAnnotations, new SmaphUtils.ComparePairsByFirstElement());
		Collections.reverse(scoreAndAnnotations);

		HashSet<ScoredAnnotation> res = new HashSet<>();

		List<Pair<Integer, Integer>> tokens = SmaphUtils.findTokensPosition(query);
		HashSet<Pair<Integer, Integer>> coveredTokens = new HashSet<>();
		
		for (Pair<Double,Annotation> pair : scoreAndAnnotations){
			Annotation annI = pair.second;
			double score = pair.first;
			
			if (score < threshold) break;
			
			boolean overlap = false;
			for (ScoredAnnotation ann : res)
				if (annI.overlaps(ann)) {
					overlap = true;
					break;
				}
			if (!overlap){
				res.add(new ScoredAnnotation(annI.getPosition(), annI.getLength(), annI.getConcept(), (float)score));
				for (Pair<Integer, Integer> token : tokens)
					if (token.first >= annI.getPosition() && token.second<=annI.getPosition() + annI.getLength())
						coveredTokens.add(token);
			}
			
			if (coveredTokens.size() == tokens.size())
				break;
		}

		return res;
	}

}
