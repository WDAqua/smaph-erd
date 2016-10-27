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

import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.datasetPlugins.ERD2014Dataset;
import it.unipi.di.acube.batframework.datasetPlugins.SMAPHDataset;
import it.unipi.di.acube.batframework.datasetPlugins.YahooWebscopeL24Dataset;
import it.unipi.di.acube.batframework.problems.C2WDataset;
import it.unipi.di.acube.batframework.systemPlugins.WATAnnotator;
import it.unipi.di.acube.batframework.utils.FreebaseApi;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.acubelab.smaph.SmaphAnnotator;
import it.acubelab.smaph.boldfilters.EditDistanceBoldFilter;
import it.acubelab.smaph.boldfilters.FrequencyBoldFilter;
import it.acubelab.smaph.entityfilters.NoEntityFilter;
import it.acubelab.smaph.linkback.DummyLinkBack;
import it.acubelab.smaph.main.ERDDatasetFilter;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Vector;

public class GenerateTrainingAndTest {

	public static void gatherExamples(SmaphAnnotator bingAnnotator,
			C2WDataset ds, BinaryExampleGatherer entityFilterGatherer,
			WikipediaToFreebase wikiToFreeb) throws Exception {
		for (int i = 0; i < ds.getSize(); i++) {
			String query = ds.getTextInstanceList().get(i);
			HashSet<Tag> goldStandard = ds.getC2WGoldStandardList().get(i);
			Vector<double[]> posEF = new Vector<>();
			Vector<double[]> negEF = new Vector<>();
			bingAnnotator.generateExamples(query, goldStandard, posEF, negEF,
					true, wikiToFreeb);
			entityFilterGatherer.addExample(posEF, negEF);
		}
	}

	public static void gatherExamplesTrainingAndDevel(
			SmaphAnnotator bingAnnotator,
			BinaryExampleGatherer trainEntityFilterGatherer,
			BinaryExampleGatherer develEntityFilterGatherer,
			WikipediaApiInterface wikiApi, WikipediaToFreebase wikiToFreebase,
			FreebaseApi freebApi) throws Exception {
		if (trainEntityFilterGatherer != null) {
			{
				C2WDataset smaphTrain = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_training.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphTrain,
						trainEntityFilterGatherer, wikiToFreebase);
			}

			{
				C2WDataset smaphTest = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_test.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphTest,
						trainEntityFilterGatherer, wikiToFreebase);
			}
			{
				C2WDataset smaphDevel = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_devel.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphDevel,
						trainEntityFilterGatherer, wikiToFreebase);
			}
			{
				C2WDataset yahoo = new ERDDatasetFilter(
						new YahooWebscopeL24Dataset(
								"datasets/yahoo_webscope_L24/ydata-search-query-log-to-entities-v1_0.xml"),
						wikiApi, wikiToFreebase);
				gatherExamples(bingAnnotator, yahoo, trainEntityFilterGatherer,
						wikiToFreebase);
			}
			{
				C2WDataset erd = new ERDDatasetFilter(new ERD2014Dataset(
						"datasets/erd2014/Trec_beta.query.txt",
						"datasets/erd2014/Trec_beta.annotation.txt", freebApi,
						wikiApi), wikiApi, wikiToFreebase);
				gatherExamples(bingAnnotator, erd, trainEntityFilterGatherer,
						wikiToFreebase);
			}
		}
		if (develEntityFilterGatherer != null) {

			C2WDataset develDs = new ERDDatasetFilter(new ERD2014Dataset(
					"datasets/erd2014/Trec_beta.query.txt",
					"datasets/erd2014/Trec_beta.annotation.txt", freebApi,
					wikiApi), wikiApi, wikiToFreebase);
			for (Tag t : develDs.getC2WGoldStandardList().get(
					develDs.getC2WGoldStandardList().size() - 1))
				System.out.println(t.getConcept());

			gatherExamples(bingAnnotator, develDs, develEntityFilterGatherer,
					wikiToFreebase);
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
