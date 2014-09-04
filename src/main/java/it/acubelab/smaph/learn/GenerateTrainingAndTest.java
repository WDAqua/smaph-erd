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
import it.acubelab.batframework.datasetPlugins.ERD2014Dataset;
import it.acubelab.batframework.datasetPlugins.SMAPHDataset;
import it.acubelab.batframework.datasetPlugins.YahooWebscopeL24Dataset;
import it.acubelab.batframework.problems.A2WDataset;
import it.acubelab.batframework.systemPlugins.WATAnnotator;
import it.acubelab.batframework.utils.FreebaseApi;
import it.acubelab.batframework.utils.Pair;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.smaph.SmaphAnnotator;
import it.acubelab.smaph.boldfilters.FrequencyBoldFilter;
import it.acubelab.smaph.entityfilters.NoEntityFilter;
import it.acubelab.smaph.linkback.DummyLinkBack;
import it.acubelab.smaph.linkback.SvmLinkBack;
import it.acubelab.smaph.main.ERDDatasetFilter;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

public class GenerateTrainingAndTest {
	
	public enum OptDataset {ERD_CHALLENGE, SMAPH_DATASET}

	public static void gatherExamples(SmaphAnnotator bingAnnotator,
			A2WDataset ds, BinaryExampleGatherer entityFilterGatherer, ExampleGatherer linkBackGatherer,
			WikipediaToFreebase wikiToFreeb) throws Exception {
		for (int i = 0; i < ds.getSize(); i++) {
			String query = ds.getTextInstanceList().get(i);
			HashSet<Tag> goldStandard = ds.getC2WGoldStandardList().get(i);
			HashSet<Annotation> goldStandardAnn = ds.getA2WGoldStandardList().get(i);
			Vector<double[]> posEF = new Vector<>();
			Vector<double[]> negEF = new Vector<>();
			List<Pair<double[], Double>> LbVectorsToF1 = new Vector<>();
			bingAnnotator.generateExamples(query, goldStandard, goldStandardAnn, posEF, negEF,
					LbVectorsToF1, true, new SvmLinkBack(null, 0.3), wikiToFreeb);
			if (entityFilterGatherer!= null)
				entityFilterGatherer.addExample(posEF, negEF);
			if (linkBackGatherer!= null)
				linkBackGatherer.addExample(LbVectorsToF1);
		}
	}

	public static void gatherExamplesTrainingAndDevel(
			SmaphAnnotator bingAnnotator,
			BinaryExampleGatherer trainEntityFilterGatherer,
			BinaryExampleGatherer develEntityFilterGatherer,
			ExampleGatherer trainLinkBackGatherer,
			ExampleGatherer develLinkBackGatherer,
			WikipediaApiInterface wikiApi, WikipediaToFreebase wikiToFreebase,
			FreebaseApi freebApi, OptDataset opt) throws Exception {
		if (trainEntityFilterGatherer != null || trainLinkBackGatherer != null) {

			if (opt == OptDataset.ERD_CHALLENGE) {

				A2WDataset smaphTrain = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_training.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphTrain,
						trainEntityFilterGatherer, trainLinkBackGatherer,
						wikiToFreebase);

				A2WDataset smaphTest = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_test.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphTest,
						trainEntityFilterGatherer, trainLinkBackGatherer,
						wikiToFreebase);

				A2WDataset smaphDevel = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_devel.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphDevel,
						trainEntityFilterGatherer, trainLinkBackGatherer,
						wikiToFreebase);

				A2WDataset yahoo =  new ERDDatasetFilter(
				new YahooWebscopeL24Dataset(
						"datasets/yahoo_webscope_L24/ydata-search-query-log-to-entities-v1_0.xml")
				 , wikiApi, wikiToFreebase);
				gatherExamples(bingAnnotator, yahoo, trainEntityFilterGatherer,
						trainLinkBackGatherer, wikiToFreebase);

				/*
				 * A2WDataset erd = new ERDDatasetFilter(new ERD2014Dataset(
				 * "datasets/erd2014/Trec_beta.query.txt",
				 * "datasets/erd2014/Trec_beta.annotation.txt", freebApi,
				 * wikiApi), wikiApi, wikiToFreebase);
				 * gatherExamples(bingAnnotator, erd, trainEntityFilterGatherer,
				 * trainLinkBackGatherer, wikiToFreebase);
				 */
			} else if (opt == OptDataset.SMAPH_DATASET) {
				A2WDataset smaphTrain = new SMAPHDataset(
						"datasets/smaph/smaph_training.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphTrain,
						trainEntityFilterGatherer, trainLinkBackGatherer,
						wikiToFreebase);

				A2WDataset smaphDevel = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_devel.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphDevel,
						trainEntityFilterGatherer, trainLinkBackGatherer,
						wikiToFreebase);

/*				A2WDataset yahoo = new YahooWebscopeL24Dataset(
						"datasets/yahoo_webscope_L24/ydata-search-query-log-to-entities-v1_0.xml");
				gatherExamples(bingAnnotator, yahoo, trainEntityFilterGatherer,
						trainLinkBackGatherer, wikiToFreebase);*/
			}
		}
		if (develEntityFilterGatherer != null || develLinkBackGatherer != null) {
			if (opt == OptDataset.ERD_CHALLENGE
					|| opt == OptDataset.SMAPH_DATASET) {
				A2WDataset smaphDevel = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_devel.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphDevel,
						develEntityFilterGatherer, develLinkBackGatherer,
						wikiToFreebase);

			}
		}

		SmaphAnnotator.flush();
		wikiApi.flush();

	}

	public static SmaphAnnotator getDefaultBingAnnotator(
			WikipediaApiInterface wikiApi, WikipediaToFreebase wikiToFreebase,
			double editDistanceSpotFilterThreshold, int wikiSearchTopK,
			String bingKey) throws FileNotFoundException,
			ClassNotFoundException, IOException {
		WATAnnotator wikiSense = new WATAnnotator("wikisense.mkapp.it", 80,
				"base", "COMMONNESS", "jaccard", "0.6", "0.0"/* minlp */, false,
				false, false);

		SmaphAnnotator bingAnnotator = new SmaphAnnotator(wikiSense,
				new FrequencyBoldFilter((float)editDistanceSpotFilterThreshold),
				new NoEntityFilter(), new DummyLinkBack(), true, true, true,
				wikiSearchTopK, false, 0, false, 0, wikiApi, bingKey);

		return bingAnnotator;
	}
}
