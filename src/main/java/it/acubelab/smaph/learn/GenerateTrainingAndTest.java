/**
 *  Copyright 2014 Marco Cornolti
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

package it.acubelab.smaph.learn;

import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.data.Tag;
import it.acubelab.batframework.datasetPlugins.SMAPHDataset;
import it.acubelab.batframework.datasetPlugins.YahooWebscopeL24Dataset;
import it.acubelab.batframework.problems.A2WDataset;
import it.acubelab.batframework.systemPlugins.WATAnnotator;
import it.acubelab.batframework.utils.FreebaseApi;
import it.acubelab.batframework.utils.Pair;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.smaph.SmaphAnnotator;
import it.acubelab.smaph.boldfilters.FrequencyBoldFilter;
import it.acubelab.smaph.entityfilters.EntityFilter;
import it.acubelab.smaph.entityfilters.LibSvmEntityFilter;
import it.acubelab.smaph.entityfilters.NoEntityFilter;
import it.acubelab.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.acubelab.smaph.learn.featurePacks.BindingFeaturePack;
import it.acubelab.smaph.learn.featurePacks.EntityFeaturePack;
import it.acubelab.smaph.learn.featurePacks.FeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;
import it.acubelab.smaph.learn.normalizer.NoFeatureNormalizer;
import it.acubelab.smaph.learn.normalizer.ScaleFeatureNormalizer;
import it.acubelab.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import it.acubelab.smaph.linkback.DummyLinkBack;
import it.acubelab.smaph.linkback.LinkBack;
import it.acubelab.smaph.linkback.SvmCollectiveLinkBack;
import it.acubelab.smaph.linkback.SvmSingleAnnotationLinkBack;
import it.acubelab.smaph.linkback.SvmSingleEntityLinkBack;
import it.acubelab.smaph.linkback.annotationRegressor.AnnotationRegressor;
import it.acubelab.smaph.linkback.annotationRegressor.LibLinearAnnotationRegressor;
import it.acubelab.smaph.linkback.bindingGenerator.DefaultBindingGenerator;
import it.acubelab.smaph.linkback.bindingRegressor.LibLinearBindingRegressor;
import it.acubelab.smaph.main.ERDDatasetFilter;
import it.acubelab.smaph.snippetannotationfilters.FrequencyAnnotationFilter;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

public class GenerateTrainingAndTest {
	
	public enum OptDataset {ERD_CHALLENGE, SMAPH_DATASET}
	public static void gatherExamples(SmaphAnnotator bingAnnotator,
			A2WDataset ds, ExampleGatherer<Tag> entityFilterGatherer, ExampleGatherer<HashSet<Annotation>> linkBackGatherer, ExampleGatherer<Annotation> annotationGatherer,
			WikipediaToFreebase wikiToFreeb, AnnotationRegressor ar, FeatureNormalizer annFn, boolean keepNEOnly) throws Exception {
		gatherExamples(bingAnnotator, ds, entityFilterGatherer,
				linkBackGatherer, annotationGatherer, wikiToFreeb, ar,annFn, keepNEOnly, -1);
	}
	public static void gatherExamples(SmaphAnnotator bingAnnotator,
			A2WDataset ds, ExampleGatherer<Tag> entityFilterGatherer, ExampleGatherer<HashSet<Annotation>> linkBackGatherer,ExampleGatherer<Annotation> annotationGatherer,
			WikipediaToFreebase wikiToFreeb, AnnotationRegressor ar, FeatureNormalizer annFn, boolean keepNEOnly, int limit) throws Exception {
			limit = limit ==-1? ds.getSize() : Math.min(limit, ds.getSize());
		for (int i = 0; i < limit; i++) {
			String query = ds.getTextInstanceList().get(i);
			HashSet<Tag> goldStandard = ds.getC2WGoldStandardList().get(i);
			HashSet<Annotation> goldStandardAnn = ds.getA2WGoldStandardList().get(i);
			List<Pair<FeaturePack<Tag>, Boolean>> EFVectorsToPresence = null;
			List<Pair<FeaturePack<HashSet<Annotation>>, Double>> LbVectorsToF1 = null;
			List<Pair<FeaturePack<Annotation>, Boolean>> annVectorsToPresence = null;
			
			if (entityFilterGatherer != null)
				EFVectorsToPresence = new Vector<>();
			if (linkBackGatherer != null)
				LbVectorsToF1 = new Vector<>();
			if (annotationGatherer != null)
				annVectorsToPresence = new Vector<>();
			
			bingAnnotator.generateExamples(query, goldStandard,
					goldStandardAnn, EFVectorsToPresence, LbVectorsToF1,
					annVectorsToPresence, keepNEOnly, new DefaultBindingGenerator(), ar,
					annFn, wikiToFreeb);
			
			if (entityFilterGatherer != null)
				entityFilterGatherer.addExample(goldBoolToDouble(EFVectorsToPresence));
			if (linkBackGatherer!= null)
				linkBackGatherer.addExample(LbVectorsToF1);
			if (annotationGatherer!= null)
				annotationGatherer.addExample(goldBoolToDouble(annVectorsToPresence));
		}
	}

	private static <E extends Object> List<Pair<FeaturePack<E>, Double>> goldBoolToDouble(
			List<Pair<FeaturePack<E>, Boolean>> eFVectorsToPresence) {
		List<Pair<FeaturePack<E>, Double>> res = new Vector<>();
		for (Pair<FeaturePack<E>, Boolean> p : eFVectorsToPresence)
			res.add(new Pair<FeaturePack<E>, Double>(p.first, p.second? 1.0
					: -1.0));
		return res;
	}

	public static void gatherExamplesTrainingAndDevel(
			SmaphAnnotator bingAnnotator,
			ExampleGatherer<Tag> trainEntityFilterGatherer,
			ExampleGatherer<Tag> develEntityFilterGatherer,
			ExampleGatherer<HashSet<Annotation>> trainLinkBackGatherer,
			ExampleGatherer<HashSet<Annotation>> develLinkBackGatherer,
			ExampleGatherer<Annotation> trainAnnotationGatherer,
			ExampleGatherer<Annotation> develAnnotationGatherer,
			AnnotationRegressor ar, FeatureNormalizer annFn,
			WikipediaApiInterface wikiApi, WikipediaToFreebase wikiToFreebase,
			FreebaseApi freebApi, OptDataset opt) throws Exception {
		if (trainEntityFilterGatherer != null || trainLinkBackGatherer != null || trainAnnotationGatherer != null) {

			if (opt == OptDataset.ERD_CHALLENGE) {

				boolean keepNEOnly = true;
				A2WDataset smaphTrain = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_training.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphTrain,
						trainEntityFilterGatherer, trainLinkBackGatherer,
						trainAnnotationGatherer, wikiToFreebase, ar, annFn, keepNEOnly);

				A2WDataset smaphTest = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_test.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphTest,
						trainEntityFilterGatherer, trainLinkBackGatherer,
						trainAnnotationGatherer, wikiToFreebase, ar, annFn, keepNEOnly);

				A2WDataset smaphDevel = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_devel.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphDevel,
						trainEntityFilterGatherer, trainLinkBackGatherer,
						trainAnnotationGatherer, wikiToFreebase, ar, annFn, keepNEOnly);

				A2WDataset yahoo = new ERDDatasetFilter(
						new YahooWebscopeL24Dataset(
								"datasets/yahoo_webscope_L24/ydata-search-query-log-to-entities-v1_0.xml"),
						wikiApi, wikiToFreebase);
				gatherExamples(bingAnnotator, yahoo, trainEntityFilterGatherer,
						trainLinkBackGatherer, trainAnnotationGatherer, wikiToFreebase, ar, annFn, keepNEOnly);

				/*A2WDataset single = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/single_test.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, single,
						trainEntityFilterGatherer, trainLinkBackGatherer,
						trainAnnotationGatherer, wikiToFreebase, ar, annFn);*/

				/*
				 * A2WDataset erd = new ERDDatasetFilter(new ERD2014Dataset(
				 * "datasets/erd2014/Trec_beta.query.txt",
				 * "datasets/erd2014/Trec_beta.annotation.txt", freebApi,
				 * wikiApi), wikiApi, wikiToFreebase);
				 * gatherExamples(bingAnnotator, erd, trainEntityFilterGatherer,
				 * trainLinkBackGatherer, wikiToFreebase);
				 */
			} else if (opt == OptDataset.SMAPH_DATASET) {
				boolean keepNEOnly = false;
				A2WDataset smaphTrain = new SMAPHDataset(
						"datasets/smaph/smaph_training.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphTrain,
						trainEntityFilterGatherer, trainLinkBackGatherer,
						trainAnnotationGatherer, wikiToFreebase, ar, annFn, keepNEOnly);

				A2WDataset smaphDevel = new SMAPHDataset(
						"datasets/smaph/smaph_devel.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphDevel,
						trainEntityFilterGatherer, trainLinkBackGatherer,
						trainAnnotationGatherer,  wikiToFreebase, ar, annFn, keepNEOnly);

/*				A2WDataset yahoo = new YahooWebscopeL24Dataset(
						"datasets/yahoo_webscope_L24/ydata-search-query-log-to-entities-v1_0.xml");
				gatherExamples(bingAnnotator, yahoo, trainEntityFilterGatherer,
						trainLinkBackGatherer, wikiToFreebase);*/
				
				/*A2WDataset smaphSingle = new SMAPHDataset(
						"datasets/smaph/single_test.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphSingle,
						trainEntityFilterGatherer, trainLinkBackGatherer,
						wikiToFreebase);*/
			}
		}
		if (develEntityFilterGatherer != null || develLinkBackGatherer != null || develAnnotationGatherer != null) {
			if (opt == OptDataset.ERD_CHALLENGE) {
				boolean keepNEOnly = true;
				A2WDataset smaphDevel = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_devel.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphDevel,
						develEntityFilterGatherer, develLinkBackGatherer, develAnnotationGatherer,
						wikiToFreebase, ar, annFn, keepNEOnly);

			}
			else if (opt == OptDataset.SMAPH_DATASET){
				boolean keepNEOnly = false;
				A2WDataset smaphTest = new SMAPHDataset(
						"datasets/smaph/smaph_test.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphTest,
						develEntityFilterGatherer, develLinkBackGatherer,develAnnotationGatherer,
						wikiToFreebase, ar, annFn, keepNEOnly);
/*				A2WDataset smaphSingle = new SMAPHDataset(
						"datasets/smaph/single_test.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphSingle,
						develEntityFilterGatherer, develLinkBackGatherer,develAnnotationGatherer,
						wikiToFreebase);*/
			}
			
		}

		SmaphAnnotator.flush();
		wikiApi.flush();

	}
	private static SmaphAnnotator getDefaultBingAnnotatorParam(
			WikipediaApiInterface wikiApi,
			double editDistanceSpotFilterThreshold, 
			String bingKey, EntityFilter entityFilter,FeatureNormalizer efNorm, LinkBack lb) throws FileNotFoundException,
			ClassNotFoundException, IOException {
				WATAnnotator wikiSense = new WATAnnotator("wikisense.mkapp.it", 80,
				"base", "COMMONNESS", "jaccard", "0.6", "0.0"/* minlp */, false,
				false, false);

				WATAnnotator watDefault = new WATAnnotator(
						"wikisense.mkapp.it", 80, "base", "COMMONNESS", "mw", "0.2",
						"0.0", false, false, false);
		return new SmaphAnnotator(wikiSense,
				new FrequencyBoldFilter((float)editDistanceSpotFilterThreshold),entityFilter
				, efNorm, lb, false, true, true,
				10, false, 0, false, 0, true, 25, watDefault, new FrequencyAnnotationFilter(0.03), wikiApi, bingKey);

	}
	public static SmaphAnnotator getDefaultBingAnnotatorGatherer(
			WikipediaApiInterface wikiApi, 
			double editDistanceSpotFilterThreshold, 
			String bingKey) throws FileNotFoundException,
			ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam( wikiApi, 
			 editDistanceSpotFilterThreshold,  
			 bingKey, new NoEntityFilter(), null, new DummyLinkBack());
	}
	public static SmaphAnnotator getDefaultBingAnnotatorEF(
			WikipediaApiInterface wikiApi, 
			double editDistanceSpotFilterThreshold, 
			String bingKey, String EFModelFileBase) throws FileNotFoundException,
			ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam( wikiApi, 
			 editDistanceSpotFilterThreshold, 
			 bingKey, new LibSvmEntityFilter(EFModelFileBase+".model"), new ZScoreFeatureNormalizer(EFModelFileBase+".zscore", new EntityFeaturePack()), new DummyLinkBack());
	}
	public static SmaphAnnotator getDefaultBingAnnotatorLB(
			WikipediaApiInterface wikiApi, 
			double editDistanceSpotFilterThreshold, 
			String bingKey, String LBModelFileBase, String LBRangeFile, String ARmodel, String ARrange) throws FileNotFoundException,
			ClassNotFoundException, IOException {
		//SvmLinkBack lb = new SvmLinkBack(LBModelFileBase, wikiApi, SvmLinkBack.BindingRegressorType.BR_LIBSVM);
		SvmCollectiveLinkBack lb = new SvmCollectiveLinkBack(wikiApi, new DefaultBindingGenerator(), new LibLinearAnnotationRegressor(ARmodel), new LibLinearBindingRegressor(LBModelFileBase), new NoFeatureNormalizer());
		return getDefaultBingAnnotatorParam( wikiApi, 
			 editDistanceSpotFilterThreshold, 
			 bingKey, new NoEntityFilter(), null, lb);
	}
	public static SmaphAnnotator getDefaultBingAnnotatorEFRegressor(
			WikipediaApiInterface wikiApi, double editDistanceSpotFilterThreshold, String bingKey,
			String EFModelFileBase) throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam( wikiApi, 
				 editDistanceSpotFilterThreshold, 
				 bingKey, new NoEntityFilter(), null, new SvmSingleEntityLinkBack(new LibSvmEntityFilter(EFModelFileBase), new ScaleFeatureNormalizer(EFModelFileBase+".range", new EntityFeaturePack()), wikiApi));
	}
	public static SmaphAnnotator getDefaultBingAnnotatorAFRegressor(
			WikipediaApiInterface wikiApi, double editDistanceSpotFilterThreshold, String bingKey,
			String AFModelFileBase, String AFScaleFile, double annotationFilterThreshold) throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam( wikiApi, 
				 editDistanceSpotFilterThreshold, 
				 bingKey, new NoEntityFilter(), null, new SvmSingleAnnotationLinkBack(new LibLinearAnnotationRegressor(AFModelFileBase), new ZScoreFeatureNormalizer(AFScaleFile, new AnnotationFeaturePack()), wikiApi, annotationFilterThreshold));
	}
}
