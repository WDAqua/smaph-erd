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

import it.acubelab.tagme.develexp.WikiSenseAnnotatorDevelopment;
import it.acubelab.batframework.problems.CandidatesSpotter;
import it.acubelab.batframework.problems.Sa2WSystem;
import it.acubelab.batframework.systemPlugins.TagmeAnnotator;
import it.acubelab.batframework.utils.*;
import it.acubelab.batframework.data.MultipleAnnotation;
import it.acubelab.erd.BingAnnotator;
import it.acubelab.erd.SmaphConfig;
import it.acubelab.erd.emptyqueryfilters.*;
import it.acubelab.erd.entityfilters.*;
import it.acubelab.erd.learn.GenerateModel;
import it.acubelab.erd.spotfilters.*;

import java.io.*;
import java.util.*;

public class Annotator {
	public static final String SMAPH_PARAMS_FORMAT = "BING-auxAnnotator=%s&minLp=%.5f&sortBy=%s&method=%s&relatedness=%s&epsilon=%.5f&spotFilter=%s&spotFilterThreshold=%f&entityFilter=%s&svmEntityFilterModelBase=%s&emptyQueryFilter=%s&svmEmptyQueryFilterModelBase=%s&entitySources=%s";
	private static WikipediaApiInterface wikiApi = null;
	private static WikipediaToFreebase wikiToFreeb = null;
	private static BingAnnotator bingAnnotator;
	private static WikiSenseAnnotatorDevelopment wikiSense = null;
	private static TagmeAnnotator tagme = null;
	private static LibSvmEntityFilter libSvmEntityFilter = null;
	private static LibSvmEmptyQueryFilter libSvmEmptyQueryFilter = null;
	private String bingKey;

	public Annotator() {
		SmaphConfig.setConfigFile("smaph-config.xml");
		bingKey = SmaphConfig.getDefaultBingKey();
		
		try {
			if (wikiApi == null)
				wikiApi = new WikipediaApiInterface("wid.cache",
						"redirect.cache");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		if (wikiToFreeb == null)
			wikiToFreeb = new WikipediaToFreebase("mapdb");

		if (wikiSense == null)
			wikiSense = new WikiSenseAnnotatorDevelopment("wikisense.mkapp.it",
					80, "base", "PAGERANK", "mw", "", "0.1", false, false,
					false);
		if (tagme == null)
			tagme = new TagmeAnnotator("http://ferrax4.itc.unipi.it:8080/tag",
					"ACUBELAB");

		if (bingAnnotator == null) {
			try {
				bingAnnotator = new BingAnnotator(wikiSense,
						new FrequencySpotFilter(7), wikiApi, wikiToFreeb,
						bingKey);
				/* BingAnnotator.setCache("bing.cache"); */
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
	}

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
			System.out.printf("Annotation: wid=%d mid=%s title=%s%n", wid, mid,
					title);
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

		/*		*//** Filter included titles */
		/*
		 * List<Annotation> filteredAnnotations= new Vector<>(); for (Annotation
		 * a : annotations) { String titleA = annToTitle.get(a); if (titleA ==
		 * null) continue; titleA =
		 * titleA.toLowerCase().replace(BingAnnotator.WIKITITLE_ENDPAR_REGEX,
		 * ""); boolean collision = false; for (Annotation b : annotations) { if
		 * (a==b) continue; String titleB = annToTitle.get(b); if (titleB ==
		 * null) continue; titleB =
		 * titleB.toLowerCase().replace(BingAnnotator.WIKITITLE_ENDPAR_REGEX,
		 * ""); if (titleB.indexOf(titleA) >=0){ collision = true;
		 * System.out.printf("Discarding %s overlapping with %s.%n",
		 * annToTitle.get(a), annToTitle.get(b)); break; } } if (!collision)
		 * filteredAnnotations.add(a); } return filteredAnnotations;
		 */

		return annotations;
	}

	public <T extends Sa2WSystem & CandidatesSpotter> List<Annotation> annotateMixedThreshold(
			String query, String textID, T annotator, float threshold) {
		List<Annotation> annotations = new ArrayList<Annotation>();

		HashSet<it.acubelab.batframework.data.ScoredAnnotation> res = annotator
				.solveSa2W(query);

		float avg = 0;
		for (it.acubelab.batframework.data.ScoredAnnotation ann : res) {
			avg += ann.getScore();
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
			System.out.printf("Annotation: wid=%d mid=%s title=%s%n", wid, mid,
					title);
			if (mid == null)
				continue;
			a.setPrimaryId(mid);
			a.setMentionText(query.substring(ann.getPosition(),
					ann.getPosition() + ann.getLength()));
			a.setScore(ann.getScore());
			annotations.add(a);
		}
		if (avg / res.size() > threshold)
			return annotations;

		annotations.clear();
		HashSet<MultipleAnnotation> mas = annotator.getSpottedCandidates(query);
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
			System.out.printf("Annotation: wid=%d mid=%s title=%s%n", wid, mid,
					title);
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

	public List<Annotation> annotate(String runId, String textID, String text) {
		if (runId.startsWith("miao")) {
			String modelFileEF = GenerateModel.getModelFileNameBaseEF(
					new Integer[] { 1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 14,
							15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25 }, 3.8,
					6.0, 0.7, 0.03, 5.0)
					+ "_" + "ANW";

			runId = String.format(SMAPH_PARAMS_FORMAT, "wikisense", 0.0,
					"PAGERANK", "base", "jaccard", 0.6f,
					"EditDistanceSpotFilter", 0.7, "SvmEntityFilter",
					modelFileEF, "NoEmptyQueryFilter", "null",
					"Annotator+NormalSearch+WikiSearch10"); // <---------------------------

			/*
			 * String modelFileEF = OnlineTester.getModelFileNameBaseEF( new
			 * Integer[]{ 1, 2, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18},
			 * 3, 4, 0.7,0.035,8.0)+"_"+"AN";
			 * 
			 * runId = String.format(ERDMain.SMAPH_PARAMS_FORMAT, "wikisense",
			 * 0.0, "COMMONNESS", "base", "mw", 0.6f, "EditDistanceSpotFilter",
			 * 0.7, "SvmEntityFilter", modelFileEF, "NoEmptyQueryFilter",
			 * "null", "Annotator+NormalSearch");
			 */
		}
		if (runId.equals("___reset_models")) {
			System.out.println("Invalidating SVM models...");
			libSvmEmptyQueryFilter = null;
			libSvmEntityFilter = null;
			return new Vector<>();
		}
		if (runId.equals("___flush_cache")) {
			System.out.println("Flushing cache...");
			try {
				BingAnnotator.flush();
				wikiApi.flush();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return new Vector<>();
		}

		System.out.printf("Annotating: runId=%s, textID=%s query=%s%n", runId,
				textID, text);
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
			double[][] paramsToTest = new double[][] { { 0.01, 1 },
					{ 0.01, 5 }, { 0.01, 10 }, { 0.03, 1 }, { 0.03, 5 },
					{ 0.03, 10 }, { 0.044, 1 }, { 0.044, 5 }, { 0.044, 10 },
					{ 0.06, 1 }, { 0.06, 5 }, { 0.06, 10 },

			};
			double[][] weightsToTest = new double[][] {

			/* { 3, 4 }, */
			{ 3.8, 6 }

			};
			Integer[][] featuresSetsToTest = new Integer[][] { { 1, 2, 3, 6, 7,
					8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
					23, 24, 25 },
			/* { 1, 2, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18},}; */
			};
			/*
			 * double[][] weightsToTest = new double[][] {
			 * 
			 * { 3.8, 6 }
			 * 
			 * }; Integer[][] featuresSetsToTest = new Integer[][] { { 1, 2, 3,
			 * 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,19,
			 * 20,21,22,23,24,25,26,27,28,29}, };
			 */
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
						"ftr_test_XXX_XXX_XXX_XXX".length()));
				int idParam = Integer.parseInt(runId.substring(
						"ftr_test_XXX_XXX__XXX_XXX".length(),
						"ftr_test_XXX_XXX_XXX_XXX_XXX".length()));

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
						"COMMONNESS", "base", "mw", 0.6f,
						"EditDistanceSpotFilter", edThr, "SvmEntityFilter",
						modelFileEF, "NoEmptyQueryFilter", "null",
						sourcesString);

			}
		}
		if (runId.equals("run5"))
			runId = "BING-auxAnnotator=wikisense&minLp=0.00000&sortBy=COMMONNESS&method=base&relatedness=mw&epsilon=0.60000&spotFilter=NoSpotFilter&spotFilterThreshold=-1.000000&entityFilter=SvmEntityFilter&svmEntityFilterModel=train_entityfilter.dat_rbf.model&emptyQueryFilter=SvmEmptyQueryFilter&svmEmptyQueryFilterModel=train_emptyqueryfilter.dat_grid.model";

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

			WikiSenseAnnotatorDevelopment auxAnnotatorService = new WikiSenseAnnotatorDevelopment(
					"wikisense.mkapp.it", 80, method, sortBy, relatedness,
					epsilon, minLp, false, false, false);
			SpotFilter spotFilter = null;
			if (spotFilterName.equals("RankWeight"))
				spotFilter = new RankWeightSpotFilter(spotFilterThreshold);
			else if (spotFilterName.equals("Frequency"))
				spotFilter = new FrequencySpotFilter(spotFilterThreshold);
			else if (spotFilterName.equals("EditDistanceSpotFilter"))
				spotFilter = new EditDistanceSpotFilter(spotFilterThreshold);
			else if (spotFilterName.equals("NoSpotFilter"))
				spotFilter = new NoSpotFilter();

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

			EmptyQueryFilter emptyQueryFilter = null;
			if (emptyQueryFilterName.equals("NoEmptyQueryFilter"))
				emptyQueryFilter = new NoEmptyQueryFilter();
			else if (emptyQueryFilterName.equals("SvmEmptyQueryFilter")) {
				synchronized (Annotator.class) {
					if (!svmEmptyQueryFilterModelBase.equals("")
							&& (libSvmEmptyQueryFilter == null || !libSvmEmptyQueryFilter
									.getModel().equals(
											svmEmptyQueryFilterModelBase))) {
						try {
							libSvmEmptyQueryFilter = new LibSvmEmptyQueryFilter(
									svmEmptyQueryFilterModelBase);
						} catch (IOException e) {
							e.printStackTrace();
							throw new RuntimeException(e);
						}
					}
					emptyQueryFilter = libSvmEmptyQueryFilter;
				}
			}

			List<Annotation> res = annotatePure(text, textID,
					new BingAnnotator(auxAnnotatorService, spotFilter,
							entityFilter, emptyQueryFilter,
							includeSourceAnnotator, includeSourceNormalSearch,
							includeSourceWikiSearch, wikiSearchPages,
							includeSourceAnnotatorCandidates,
							topKannotatorCandidates,
							includeSourceRelatedSearch, topKRelatedSearch,
							wikiApi, wikiToFreeb, bingKey));

			/*
			 * if (!res.isEmpty()){ /////////////////////////////////////////
			 * ////////////////////////////////////////// List<Annotation>
			 * dummyRes = new Vector<>(); Annotation a = new Annotation();
			 * a.setQid(textID); a.setInterpretationSet(0); String title =
			 * "Scorrano"; String mid = wikiToFreeb.getFreebaseId(title);
			 * System.out.printf("Annotation: mid=%s title=%s%n", mid, title);
			 * if (mid == null) throw new RuntimeException();
			 * a.setPrimaryId(mid); a.setMentionText("null"); a.setScore(0.3f);
			 * dummyRes.add(a); return dummyRes; }
			 */

			return res;
		}

		if (runId.equals("run1"))
			return annotatePure(text, textID, wikiSense);
		else if (runId.equals("run3"))
			return annotatePure(text, textID, tagme);
		else if (runId.equals("run2"))
			return annotateCommonness(text, textID, wikiSense);
		else if (runId.matches("\\d*\\.\\d*")) {
			float threshold = Float.parseFloat(runId);
			return annotateMixedThreshold(text, textID, wikiSense, threshold);
		} else if (runId.equals("run4"))
			return annotatePure(text, textID, bingAnnotator);
		else if (runId.equals("void"))
			return new Vector<>();

		throw new RuntimeException("unrecognized runID=" + runId);

	}

	public static HashSet<MultipleAnnotation> deleteOverlappingAnnotations(
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
