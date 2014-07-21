package it.acubelab.erd.learn;

import it.acubelab.batframework.data.Tag;
import it.acubelab.batframework.datasetPlugins.ERD2014Dataset;
import it.acubelab.batframework.datasetPlugins.SMAPHDataset;
import it.acubelab.batframework.datasetPlugins.YahooWebscopeL24Dataset;
import it.acubelab.batframework.problems.C2WDataset;
import it.acubelab.batframework.utils.FreebaseApi;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.erd.SmaphAnnotator;
import it.acubelab.erd.boldfilters.EditDistanceBoldFilter;
import it.acubelab.erd.entityfilters.NoEntityFilter;
import it.acubelab.erd.main.ERDDatasetFilter;
import it.acubelab.tagme.develexp.WikiSenseAnnotatorDevelopment;
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
						trainEntityFilterGatherer,
						wikiToFreebase);
			}

			{
				C2WDataset smaphTest = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_test.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphTest,
						trainEntityFilterGatherer,
						wikiToFreebase);
			}
			{
				C2WDataset smaphDevel = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_devel.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphDevel,
						trainEntityFilterGatherer,
						wikiToFreebase);
			}
			{
				C2WDataset yahoo = new ERDDatasetFilter(
						new YahooWebscopeL24Dataset(
								"datasets/yahoo_webscope_L24/ydata-search-query-log-to-entities-v1_0.xml"),
						wikiApi, wikiToFreebase);
				;
				gatherExamples(bingAnnotator, yahoo, trainEntityFilterGatherer, wikiToFreebase);
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

			gatherExamples(bingAnnotator, develDs, develEntityFilterGatherer, wikiToFreebase);
		}

		SmaphAnnotator.flush();
		wikiApi.flush();

	}

	public static SmaphAnnotator getDefaultBingAnnotator(
			WikipediaApiInterface wikiApi, WikipediaToFreebase wikiToFreebase,
			double editDistanceSpotFilterThreshold, int wikiSearchTopK,
			String bingKey) throws FileNotFoundException,
			ClassNotFoundException, IOException {
		WikiSenseAnnotatorDevelopment wikiSense = new WikiSenseAnnotatorDevelopment(
				"wikisense.mkapp.it", 80, "base", "PAGERANK", "jaccard", "0.6",
				"0.0"/* minlp */, true, false, false);

		SmaphAnnotator bingAnnotator = new SmaphAnnotator(wikiSense,
				new EditDistanceBoldFilter(editDistanceSpotFilterThreshold),
				new NoEntityFilter(), true, true, true, wikiSearchTopK, false,
				0, false, 0, wikiApi, bingKey);

		return bingAnnotator;
	}

	public static void main(String[] args) throws Exception {
		Locale.setDefault(Locale.US);
		// SmaphAnnotatorDebugger.disable();
		String bingKey = "";
		String freebKey = "";
		WikipediaApiInterface wikiApi = new WikipediaApiInterface(
				"benchmark/cache/wid.cache", "benchmark/cache/redirect.cache");
		FreebaseApi freebApi = new FreebaseApi(freebKey, "freeb.cache");

		WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");
		SmaphAnnotator bingAnnotator = getDefaultBingAnnotator(wikiApi,
				wikiToFreebase, 0.7, 10, bingKey);
		SmaphAnnotator.setCache("bing.cache.full");

		BinaryExampleGatherer trainEntityFilterGatherer = new BinaryExampleGatherer();
		BinaryExampleGatherer develEntityFilterGatherer = new BinaryExampleGatherer();
		gatherExamplesTrainingAndDevel(bingAnnotator,
				trainEntityFilterGatherer,
				develEntityFilterGatherer, wikiApi,
				wikiToFreebase, freebApi);
		trainEntityFilterGatherer.dumpExamplesLibSvm("train_entityfilter.dat");
		develEntityFilterGatherer.dumpExamplesLibSvm("devel_entityfilter.dat");
		
		SmaphAnnotator.flush();
		wikiApi.flush();
	}
}
