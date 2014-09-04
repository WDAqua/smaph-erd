/**
 *  Copyright 2014 Diego Ceccarelli
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
package it.cnr.isti.hpc.erd;

import it.acubelab.smaph.SmaphAnnotator;
import it.acubelab.smaph.SmaphConfig;
import it.acubelab.smaph.boldfilters.*;
import it.acubelab.smaph.entityfilters.*;
import it.acubelab.smaph.learn.GenerateModel;
import it.acubelab.smaph.linkback.BaselineLinkBack;
import it.acubelab.smaph.linkback.DummyLinkBack;
import it.acubelab.batframework.problems.CandidatesSpotter;
import it.acubelab.batframework.problems.Sa2WSystem;
import it.acubelab.batframework.systemPlugins.TagmeAnnotator;
import it.acubelab.batframework.systemPlugins.WATAnnotator;
import it.acubelab.batframework.utils.*;
import it.acubelab.batframework.data.MultipleAnnotation;

import java.io.*;
import java.util.*;

public class Annotator {
	public static final String SMAPH_PARAMS_FORMAT = "BING-auxAnnotator=%s&minLp=%.5f&sortBy=%s&method=%s&relatedness=%s&epsilon=%.5f&spotFilter=%s&spotFilterThreshold=%f&entityFilter=%s&svmEntityFilterModelBase=%s&emptyQueryFilter=%s&svmEmptyQueryFilterModelBase=%s&entitySources=%s";
	private static WikipediaApiInterface wikiApi = null;
	private static WikipediaToFreebase wikiToFreeb = null;
	private static TagmeAnnotator tagme = null;
	private static LibSvmEntityFilter libSvmEntityFilter = null;
	private String bingKey;
	private String tagmeKey;
	private String tagmeHost;

	public Annotator() {
		SmaphConfig.setConfigFile("smaph-config.xml");
		bingKey = SmaphConfig.getDefaultBingKey();
		String bingCache = SmaphConfig.getDefaultBingCache();
		
		try {
			if (wikiApi == null)
				wikiApi = new WikipediaApiInterface("wid.cache",
						"redirect.cache");
			if (bingCache != null)
				SmaphAnnotator.setCache(bingCache);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		if (wikiToFreeb == null)
			wikiToFreeb = new WikipediaToFreebase("mapdb");
	}

	/**
	 * Annotate a query with an annotator 'as is', picking the candidate with
	 * highest commonness.
	 * 
	 * @param query
	 *            the query
	 * @param textID
	 *            an unique id for the query.
	 * @param spotter
	 *            the annotator that spots candidates.
	 * @return the list of annotations.
	 */
	public List<Annotation> annotateCommonness(String query, String textID,
			CandidatesSpotter spotter) {
		List<Annotation> annotations = new ArrayList<Annotation>();
		HashSet<MultipleAnnotation> mas = spotter.getSpottedCandidates(query);
		mas = deleteOverlappingAnnotations(mas);
		for (MultipleAnnotation ma : mas) {
			Annotation a = new Annotation();
			a.setQid(textID);
			a.setInterpretationSet(0);
			int wid = ma.getCandidates()[0];
			String title = null;
			try {
				title = wikiApi.getTitlebyId(wid);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			String mid = wikiToFreeb.getFreebaseId(title);
			if (mid == null)
				continue;
			a.setPrimaryId(mid);
			a.setMentionText(query.substring(ma.getPosition(), ma.getPosition()
					+ ma.getLength()));
			a.setScore(1.0f);
			annotations.add(a);
		}
		return annotations;
	}

	/**
	 * Annotate a query with an annotator 'as is'.
	 * 
	 * @param query
	 *            the query
	 * @param textID
	 *            an unique id for the query.
	 * @param annotator
	 *            the annotator to tag the query.
	 * @return the list of annotations.
	 */
	public List<Annotation> annotatePure(String query, String textID,
			Sa2WSystem annotator) {
		List<Annotation> annotations = new ArrayList<Annotation>();
		HashSet<it.acubelab.batframework.data.ScoredAnnotation> res = annotator
				.solveSa2W(query);
		System.out.printf(annotator.getName() + " found %d annotations.%n",
				res.size());
		HashMap<Annotation, String> annToTitle = new HashMap<>();
		for (it.acubelab.batframework.data.ScoredAnnotation ann : res) {
			Annotation a = new Annotation();
			a.setQid(textID);
			a.setInterpretationSet(0);
			int wid = ann.getConcept();
			String title = null;
			try {
				title = wikiApi.getTitlebyId(wid);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			String mid = wikiToFreeb.getFreebaseId(title);
			annToTitle.put(a, title);
			System.out.printf("Annotation: wid=%d mid=%s title=%s%n", wid, mid,
					title);
			if (mid == null)
				continue;
			a.setPrimaryId(mid);
			a.setMentionText("null");
			/*
			 * a.setMentionText(query.substring(ann.getPosition(),
			 * ann.getPosition() + ann.getLength()));
			 */
			a.setScore(ann.getScore());
			annotations.add(a);
		}
		return annotations;
	}

	/**
	 * Handler for an annotation call. Depending on the runId, it calls
	 * different methods to annotate a query.
	 * 
	 * @param runId
	 *            the runId from which the configuation is picked.
	 * @param query
	 *            the query.
	 * @param textID
	 *            an unique id for the query.
	 * @return the annotations of the query.
	 */
	public List<Annotation> annotate(String runId, String textID, String query) {
		if (runId.startsWith("miao")) {
			String modelFileEF = GenerateModel.getModelFileNameBaseEF(
					new Integer[] {1, 2, 3, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 33, 34, 35, 36, 37},
					3.8, 5.6, 0.06, 0.03, 5.0)
					+ "_" + "ANW";

			runId = String.format(SMAPH_PARAMS_FORMAT, "wikisense", 0.0,
					"COMMONNESS", "base", "jaccard", 0.6f,
					"Frequency", 0.06, "SvmEntityFilter",
					modelFileEF, "NoEmptyQueryFilter", "null",
					"Annotator+NormalSearch+WikiSearch10"); // <---------------------------
		}
		if (runId.equals("___reset_models")) {
			System.out.println("Invalidating SVM models...");
			libSvmEntityFilter = null;
			return new Vector<>();
		}
		if (runId.equals("___flush_cache")) {
			System.out.println("Flushing cache...");
			try {
				SmaphAnnotator.flush();
				wikiApi.flush();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return new Vector<>();
		}

		String auxAnnotator = "";
		String minLp = "";
		String sortBy = "";
		String method = "";
		String relatedness = "";
		String epsilon = "";
		String spotFilterName = "";
		String entityFilterName = "";
		String svmEntityFilterModelBase = "";
		float spotFilterThreshold = -1;
		String emptyQueryFilterName = "";
		String svmEmptyQueryFilterModelBase = "";
		Vector<String> entitySources = new Vector<>();
		boolean includeSourceAnnotator = false;
		boolean includeSourceNormalSearch = false;
		boolean includeSourceWikiSearch = false;
		int wikiSearchPages = 0;
		boolean includeSourceAnnotatorCandidates = false;
		int topKannotatorCandidates = 0;
		boolean includeSourceRelatedSearch = false;
		int topKRelatedSearch = 0;

		{
			double[][] paramsToTest = new double[][] { /*{ 0.01, 1 },
					{ 0.01, 5 }, { 0.01, 10 }, { 0.03, 1 }, { 0.03, 5 },
					{ 0.03, 10 }, { 0.044, 1 }, { 0.044, 5 }, { 0.044, 10 },
					{ 0.06, 1 }, { 0.06, 5 }, { 0.06, 10 },*/
					{ 0.03, 5 },
			};
			double[][] weightsToTest = new double[][] {
					{ 3.8, 3.5 },
					{ 3.8, 4.0 },
					{ 3.8, 4.3 },
					{ 3.8, 4.6 },
					{ 3.8, 5.0 },
					{ 3.8, 5.3 },
					{ 3.8, 5.6 },

			};
			Integer[][] featuresSetsToTest = new Integer[][] {
					{1, 2, 3, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 33, 34, 35, 36, 37},
			};
			if (runId.startsWith("ftr_test_")) {
				String sources = runId.substring("ftr_test_".length(),
						"ftr_test_XXX".length());
				int idftr = Integer.parseInt(runId.substring(
						"ftr_test_XXX_".length(), "ftr_test_XXX_XXX".length()));
				int idWeight = Integer.parseInt(runId.substring(
						"ftr_test_XXX_XXX_".length(),
						"ftr_test_XXX_XXX_XXX".length()));
				double edThr = Double.parseDouble(runId.substring(
						"ftr_test_XXX_XXX_XXX_".length(),
						"ftr_test_XXX_XXX_XXX_XXXX".length()));
				int idParam = Integer.parseInt(runId.substring(
						"ftr_test_XXX_XXX_XXX_XXXX_".length(),
						"ftr_test_XXX_XXX_XXX_XXXX_XXX".length()));

				String sourcesString = "";
				for (char c : sources.toCharArray())
					if (c == 'A')
						sourcesString += "Annotator+";
					else if (c == 'W')
						sourcesString += "WikiSearch10+";
					else if (c == 'N')
						sourcesString += "NormalSearch+";
				sourcesString = sourcesString.substring(0,
						sourcesString.length() - 1);

				double wPos = weightsToTest[idWeight][0];
				double wNeg = weightsToTest[idWeight][1];
				double gamma = paramsToTest[idParam][0];
				double C = paramsToTest[idParam][1];
				String modelFileEF = GenerateModel.getModelFileNameBaseEF(
						featuresSetsToTest[idftr], wPos, wNeg, edThr, gamma, C)
						+ "_" + sources;
				runId = String.format(SMAPH_PARAMS_FORMAT, "wikisense", 0.0,
						"COMMONNESS", "base", "jaccard", 0.6f,
						"Frequency", edThr, "SvmEntityFilter",
						modelFileEF, "NoEmptyQueryFilter", "null",
						sourcesString);

			}
		}
		if (runId.startsWith("BING-")) {
			for (String paramSet : runId.substring(5).split("&")) {
				if (paramSet.split("=").length == 1)
					continue;
				String paramName = paramSet.split("=")[0];
				String paramValue = paramSet.split("=")[1];
				if (paramName.equals("auxAnnotator"))
					auxAnnotator = paramValue;
				if (paramName.equals("minLp"))
					minLp = paramValue;
				if (paramName.equals("sortBy"))
					sortBy = paramValue;
				if (paramName.equals("method"))
					method = paramValue;
				if (paramName.equals("relatedness"))
					relatedness = paramValue;
				if (paramName.equals("spotFilterThreshold"))
					spotFilterThreshold = Float.parseFloat(paramValue);
				if (paramName.equals("epsilon"))
					epsilon = paramValue;
				if (paramName.equals("spotFilter"))
					spotFilterName = paramValue;
				if (paramName.equals("entityFilter"))
					entityFilterName = paramValue;
				if (paramName.equals("svmEntityFilterModelBase"))
					svmEntityFilterModelBase = paramValue;
				if (paramName.equals("emptyQueryFilter"))
					emptyQueryFilterName = paramValue;
				if (paramName.equals("svmEmptyQueryFilterModelBase"))
					svmEmptyQueryFilterModelBase = paramValue;
				if (paramName.equals("entitySources"))
					for (String srcName : paramValue.split("\\+"))
						entitySources.add(srcName);

			}
			int sourcesCount = 0;
			if (entitySources.contains("Annotator")) {
				includeSourceAnnotator = true;
				sourcesCount++;
			}
			if (entitySources.contains("NormalSearch")) {
				includeSourceNormalSearch = true;
				sourcesCount++;
			}
			for (String src : entitySources) {
				if (src.startsWith("WikiSearch")) {
					includeSourceWikiSearch = true;
					wikiSearchPages = Integer.parseInt(src
							.substring("WikiSearch".length()));
					sourcesCount++;

				}
				if (src.startsWith("AnnotatorCandidates")) {
					includeSourceAnnotatorCandidates = true;
					topKannotatorCandidates = Integer.parseInt(src
							.substring("AnnotatorCandidates".length()));
					sourcesCount++;
				}
				if (src.startsWith("RelatedSearch")) {
					includeSourceRelatedSearch = true;
					topKRelatedSearch = Integer.parseInt(src
							.substring("RelatedSearch".length()));
					sourcesCount++;
				}
			}
			if (sourcesCount != entitySources.size())
				throw new RuntimeException("Unrecognized Source.");
			System.out
					.printf("Parameters: annotator=%s, minLp=%s, sortBy=%s, method=%s, relatedness=%s, spotFilter=%s, spotManagerThreshold=%f entityFilter=%s svmEntityFilterModel=%s emptyQueryFilterName=%s svmEmptyQueryFilterModel=%s includeSourceAnnotator=%b includeSourceNormalSearch=%b includeSourceWikiSearch=%b (wikiSearchPages=%d) includeSourceAnnotatorCandidates=%b (topKannotatorCandidates=%d)%n",
							auxAnnotator, minLp, sortBy, method, relatedness,
							spotFilterName, spotFilterThreshold,
							entityFilterName, svmEntityFilterModelBase,
							emptyQueryFilterName, svmEmptyQueryFilterModelBase,
							includeSourceAnnotator, includeSourceNormalSearch,
							includeSourceWikiSearch, wikiSearchPages,
							includeSourceAnnotatorCandidates,
							topKannotatorCandidates);

			WATAnnotator auxAnnotatorService = new WATAnnotator(
					"wikisense.mkapp.it", 80, method, sortBy, relatedness,
					epsilon, minLp, false, false, false);
			BoldFilter spotFilter = null;
			if (spotFilterName.equals("RankWeight"))
				spotFilter = new RankWeightBoldFilter(spotFilterThreshold);
			else if (spotFilterName.equals("Frequency"))
				spotFilter = new FrequencyBoldFilter(spotFilterThreshold);
			else if (spotFilterName.equals("EditDistanceSpotFilter"))
				spotFilter = new EditDistanceBoldFilter(spotFilterThreshold);
			else if (spotFilterName.equals("NoSpotFilter"))
				spotFilter = new NoBoldFilter();

			EntityFilter entityFilter = null;
			if (entityFilterName.equals("NoEntityFilter"))
				entityFilter = new NoEntityFilter();
			else if (entityFilterName.equals("SvmEntityFilter")) {
				synchronized (Annotator.class) {

					if (!svmEntityFilterModelBase.equals("")
							&& (libSvmEntityFilter == null || !libSvmEntityFilter
									.getModel()
									.equals(svmEntityFilterModelBase))) {
						try {
							libSvmEntityFilter = new LibSvmEntityFilter(
									svmEntityFilterModelBase);
						} catch (IOException e) {
							e.printStackTrace();
							throw new RuntimeException(e);
						}
					}
					entityFilter = libSvmEntityFilter;
				}
			}

			List<Annotation> res = annotatePure(query, textID,
					new SmaphAnnotator(auxAnnotatorService, spotFilter,
							entityFilter, new DummyLinkBack(),
							includeSourceAnnotator, includeSourceNormalSearch,
							includeSourceWikiSearch, wikiSearchPages,
							includeSourceAnnotatorCandidates,
							topKannotatorCandidates,
							includeSourceRelatedSearch, topKRelatedSearch,
							wikiApi, bingKey));

			return res;
		}

		else if (runId.equals("tagme")) {
			if (tagme == null) {
				tagmeHost = SmaphConfig.getDefaultTagmeHost();
				tagmeKey = SmaphConfig.getDefaultTagmeKey();
				tagme = new TagmeAnnotator(tagmeHost, tagmeKey);
			}
			return annotatePure(query, textID, tagme);
		} else if (runId.equals("void"))
			return new Vector<>();

		throw new RuntimeException("unrecognized runID=" + runId);

	}

	private static HashSet<MultipleAnnotation> deleteOverlappingAnnotations(
			HashSet<MultipleAnnotation> anns) {
		Vector<MultipleAnnotation> annsList = new Vector<MultipleAnnotation>(
				anns);
		HashSet<MultipleAnnotation> res = new HashSet<MultipleAnnotation>();
		Collections.sort(annsList);

		for (int i = 0; i < annsList.size(); i++) {
			MultipleAnnotation bestCandidate = annsList.get(i);
			int j = i + 1;
			while (j < annsList.size()
					&& bestCandidate.overlaps(annsList.get(j))) {
				if (bestCandidate.getLength() < annsList.get(j).getLength())
					bestCandidate = annsList.get(j);
				j++;
			}
			i = j - 1;
			res.add(bestCandidate);
		}
		return res;

	}

}
