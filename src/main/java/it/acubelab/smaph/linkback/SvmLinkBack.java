package it.acubelab.smaph.linkback;

import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.data.ScoredAnnotation;
import it.acubelab.batframework.data.Tag;
import it.acubelab.batframework.utils.Pair;
import it.acubelab.smaph.SmaphUtils;
import it.acubelab.smaph.entityfilters.LibSvmEntityFilter;
import it.acubelab.smaph.learn.LibSvmModel;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import org.apache.commons.lang.ArrayUtils;

public class SvmLinkBack implements LinkBack {
	private static final int MAX_BIO_SEQUENCES = 1000;
	private static final int MAX_BINDINGS = 1000;
	private double editDistanceThreshold;
	private SvmBindingRegressor bindingRegressorModel;

	public SvmLinkBack(String modelFileBase, double editDistanceThreshold)
			throws IOException {
		if (modelFileBase != null)
			bindingRegressorModel = new SvmBindingRegressor(modelFileBase);
		this.editDistanceThreshold = editDistanceThreshold;
	}

	private static void populateBindings(List<Tag> chosenCandidates,
			List<List<Tag>> candidates, List<List<Tag>> bindings) {
		if (bindings.size() > MAX_BINDINGS)
			return;
		if (chosenCandidates.size() == candidates.size()) {
			bindings.add(new Vector<Tag>(chosenCandidates));
			return;
		}
		List<Tag> candidatesToExpand = candidates.get(chosenCandidates.size());
		for (Tag candidate : candidatesToExpand) {
			List<Tag> nextChosenCandidates = new Vector<>(chosenCandidates);
			nextChosenCandidates.add(candidate);
			populateBindings(nextChosenCandidates, candidates, bindings);
		}
	}

	public List<HashSet<Annotation>> getAllBindings(String query,
			HashMap<Tag, String[]> entityToTexts) {
		HashSet<HashSet<Annotation>> insertedAnnotationSets = new HashSet<>();
		List<HashSet<Annotation>> annotationSets = new Vector<>();
		List<List<Pair<Integer, Integer>>> segmentations = SmaphUtils
				.getSegmentations(query, MAX_BIO_SEQUENCES);
		for (List<Pair<Integer, Integer>> segmentation : segmentations) {
			List<List<Tag>> candidatesForSegmentation = new Vector<>();
			for (Pair<Integer, Integer> segment : segmentation) {
				List<Tag> candidatesForSegment = new Vector<>();
				candidatesForSegmentation.add(candidatesForSegment);
				// This segment may not be linked to any entity.
				candidatesForSegment.add(new Tag(-1));
				for (Tag tag : entityToTexts.keySet()) {
					String[] tagTexts = entityToTexts.get(tag);
					double minEditDistance = 1.0;
					for (String tagText : tagTexts)
						minEditDistance = Math.min(
								SmaphUtils.getNormEditDistanceLC(query
										.substring(segment.first,
												segment.second), tagText),
								minEditDistance);
					if (minEditDistance <= editDistanceThreshold)
						candidatesForSegment.add(tag);
				}
			}
			List<List<Tag>> bindingsForSegmentation = new Vector<>();
			populateBindings(new Vector<Tag>(), candidatesForSegmentation,
					bindingsForSegmentation);
			for (List<Tag> tags : bindingsForSegmentation) {
				HashSet<Annotation> annotations = new HashSet<>();
				for (int i = 0; i < tags.size(); i++)
					if (tags.get(i).getConcept() != -1)
						annotations.add(new Annotation(
								segmentation.get(i).first,
								segmentation.get(i).second
										- segmentation.get(i).first, tags
										.get(i).getConcept()));
				if (!insertedAnnotationSets.contains(annotations)) {
					annotationSets.add(annotations);
					insertedAnnotationSets.add(annotations);
				}
			}
		}
		return annotationSets;
	}

	@Override
	public HashSet<ScoredAnnotation> linkBack(String query,
			HashSet<Tag> acceptedEntities, HashMap<String, Tag> spotToAnnotation, HashMap<Tag,List<HashMap<String,Double>>> entityToFtrVects) {

		HashMap<Tag, String[]> entitiesToBolds = SmaphUtils.getEntitiesToTexts(
				spotToAnnotation, acceptedEntities);

		// Generate all possible bindings
		List<HashSet<Annotation>> bindings = getAllBindings(query,
				entitiesToBolds);

		// Predict a score and pick the best-performing
		HashSet<Annotation> bestBinding = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (HashSet<Annotation> binding : bindings) {
			HashMap<String, Double> features = getFeatures(binding, query,
					entitiesToBolds, entityToFtrVects);
			if (bindingRegressorModel.predictScore(features) > bestScore)
				bestBinding = binding;
		}

		HashSet<ScoredAnnotation> scoredBestBinding = new HashSet<>();
		for (Annotation ann : bestBinding)
			scoredBestBinding.add(new ScoredAnnotation(ann.getPosition(), ann
					.getLength(), ann.getConcept(), 1.0f));
		return scoredBestBinding;
	}
	
	/**Given a list of feature vectors associated to an entity, return a single feature vector (in hashmap form). This vector will containing the max, min, and avg of for [0,1] features and the sum for counting features.
	 * @param allFtrVects
	 * @return a single representation
	 */
	private HashMap<String,Double> collapseEntityFeatures(List<HashMap<String,Double>> allFtrVects){
		//count
		HashMap<String, Integer> ftrCount = new HashMap<>();
		for (HashMap<String,Double> ftrVectToMerge : allFtrVects)
			for (String ftrName: ftrVectToMerge.keySet()){
				if (!ftrCount.containsKey(ftrName))
					ftrCount.put(ftrName, 0);
				ftrCount.put(ftrName, ftrCount.get(ftrName) +1);
			}

		
		HashMap<String,Double> entitySetFeatures = new HashMap<>();
		for (HashMap<String,Double> ftrVectToMerge : allFtrVects){
			for (String ftrName : ftrVectToMerge.keySet()){
				if (ftrName.startsWith("s1_")||ftrName.startsWith("s2_")||ftrName.startsWith("s3_")){
					if (!entitySetFeatures.containsKey("min_"+ftrName))
						entitySetFeatures.put("min_"+ftrName, Double.POSITIVE_INFINITY);
					if (!entitySetFeatures.containsKey("max_"+ftrName))
						entitySetFeatures.put("max_"+ftrName, Double.NEGATIVE_INFINITY);
					if (!entitySetFeatures.containsKey("avg_"+ftrName))
						entitySetFeatures.put("avg_"+ftrName, 0.0);
					
					double ftrValue = ftrVectToMerge.get(ftrName);
					entitySetFeatures.put("min_"+ftrName, Math.min(entitySetFeatures.get("min_"+ftrName), ftrValue));
					entitySetFeatures.put("max_"+ftrName, Math.max(entitySetFeatures.get("max_"+ftrName), ftrValue));
					entitySetFeatures.put("avg_"+ftrName, entitySetFeatures.get("avg_"+ftrName) + ftrValue/ftrCount.get(ftrName));
					
				}
				else if (ftrName.startsWith("is_")){
					String key = "count_"+ftrName;
					if (!entitySetFeatures.containsKey(key))
						entitySetFeatures.put(key, 0.0);
					entitySetFeatures.put(key, entitySetFeatures.get(key)+1);
				}
			}
		}
		return entitySetFeatures;
	}

	private HashMap<String,Double> collapseEntitySetFeatures(HashSet<Tag> entitySet, HashMap<Tag,List<HashMap<String,Double>>> entityToFeatureVectors){
		List<HashMap<String,Double>> allEntitiesFeatures = new Vector<>();
		for (Tag t: entitySet)
			allEntitiesFeatures.addAll(entityToFeatureVectors.get(t));
		return collapseEntityFeatures(allEntitiesFeatures);
	}



	public HashMap<String, Double> getFeatures(HashSet<Annotation> binding,
			String query, HashMap<Tag, String[]> entitiesToBolds, HashMap<Tag, List<HashMap<String,Double>>> entityToFeatureVectors) {
		
		HashSet<Tag> selectedEntities = new HashSet<>();
		for (Annotation ann : binding)
			selectedEntities.add(new Tag(ann.getConcept()));
		HashMap<String, Double> features = collapseEntitySetFeatures(selectedEntities, entityToFeatureVectors);
				
		double minMinED = 1.0;
		double maxMinED = 0.0;
		double avgMinED = 0.0;
		for (Annotation a : binding) {
			Tag t = new Tag(a.getConcept());
			double minED = 1.0;
			for (String bold : entitiesToBolds.get(t))
				minED = Math
						.min(SmaphUtils.getMinEditDist(
								query.substring(a.getPosition(),
										a.getPosition() + a.getLength()), bold),
								minED);
			minMinED = Math.min(minED, minMinED);
			maxMinED = Math.max(minED, maxMinED);
			avgMinED += minED;
		}
		avgMinED = binding.size() == 0 ? 0 : avgMinED / binding.size();

		features.put("min_min_edit_distance", minMinED);
		features.put("avg_min_edit_distance", maxMinED);
		features.put("max_min_edit_distance", avgMinED);

		return features;
	}
}
