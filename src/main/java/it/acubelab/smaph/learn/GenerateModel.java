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

import it.acubelab.batframework.metrics.MetricsResultSet;
import it.acubelab.batframework.systemPlugins.WATAnnotator;
import it.acubelab.batframework.utils.FreebaseApi;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.smaph.SmaphAnnotator;
import it.acubelab.smaph.SmaphAnnotatorDebugger;
import it.acubelab.smaph.SmaphConfig;
import it.acubelab.smaph.SmaphUtils;
import it.acubelab.smaph.learn.GenerateTrainingAndTest.OptDataset;
import it.acubelab.smaph.linkback.SvmBindingRegressor;
import it.acubelab.smaph.linkback.SvmLinkBack;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.util.*;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

import org.apache.commons.lang3.tuple.Triple;

public class GenerateModel {
	public static void main(String[] args) throws Exception {
		Locale.setDefault(Locale.US);
		generateEFModel();
		//generateLBModel();
		WATAnnotator.flush();
	}

	public static void generateLBModel() throws Exception {

		SmaphConfig.setConfigFile("smaph-config.xml");
		String bingKey = SmaphConfig.getDefaultBingKey();
		String freebKey = SmaphConfig.getDefaultFreebaseKey();
		String freebCache = SmaphConfig.getDefaultFreebaseCache();
		SmaphAnnotator.setCache(SmaphConfig.getDefaultBingCache());
		WikipediaApiInterface wikiApi = new WikipediaApiInterface("wid.cache",
				"redirect.cache");
		FreebaseApi freebApi = new FreebaseApi(freebKey, freebCache);
		Integer[][] featuresSetsToTest = new Integer[][] { SmaphUtils
				.getAllFtrVect(SvmBindingRegressor.getFeatureNames().length)
				.toArray(new Integer[] {}) };
		OptDataset opt = OptDataset.SMAPH_DATASET;
		
		int wikiSearckTopK = 10;
		String filePrefix = "_ANW";
		WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");
		List<ModelConfigurationResult> mcrs = new Vector<>();
		for (double boldFilterThr = 0.06; boldFilterThr <= 0.06; boldFilterThr += 0.02) {
			SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
					.getDefaultBingAnnotator(wikiApi, wikiToFreebase,
							boldFilterThr, wikiSearckTopK, bingKey);
			WATAnnotator.setCache("wikisense.cache");

			ExampleGatherer trainLinkBackGatherer = new ExampleGatherer();
			ExampleGatherer develLinkBackGatherer = new ExampleGatherer();
			GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
					bingAnnotator, null, null, trainLinkBackGatherer,
					develLinkBackGatherer, wikiApi, wikiToFreebase, freebApi, opt);
			trainLinkBackGatherer.dumpExamplesLibSvm("train.dat");
			develLinkBackGatherer.dumpExamplesLibSvm("devel.dat");

			int count = 0;
			for (Integer[] ftrToTestArray : featuresSetsToTest) {
				double gamma = 1.0 / ftrToTestArray.length;
				double C = 5.0;
				Vector<Integer> features = new Vector<>(
						Arrays.asList(ftrToTestArray));
				String fileBase = getModelFileNameBaseLB(
						features.toArray(new Integer[0]), boldFilterThr, gamma,
						C) + filePrefix;

				Triple<svm_problem, double[], double[]> ftrsMinsMaxs = TuneModel
						.getScaledTrainProblem(features, trainLinkBackGatherer);
				svm_problem trainProblem = ftrsMinsMaxs.getLeft();

				LibSvmUtils.dumpRanges(ftrsMinsMaxs.getMiddle(),
						ftrsMinsMaxs.getRight(), fileBase + ".range");
				svm_parameter param = TuneModel.getParametersLB(gamma, C);
				svm_model model = TuneModel.trainModel(param, features,
						trainProblem);
				svm.svm_save_model(fileBase + ".model", model);
				List<svm_problem> testProblems = TuneModel
						.getScaledTestProblems(features, develLinkBackGatherer,
								ftrsMinsMaxs.getMiddle(),
								ftrsMinsMaxs.getRight());
				double highestPredictionGoldAvg = 0.0;
				double highestGoldAvg = 0.0;
				for (svm_problem testProblem : testProblems) {
					double highestPrediction = Double.NEGATIVE_INFINITY;
					double highestPredictionGold = Double.NEGATIVE_INFINITY;
					double highestGold = Double.NEGATIVE_INFINITY;
					for (int j = 0; j < testProblem.l; j++) {
						svm_node[] svmNode = testProblem.x[j];
						double gold = testProblem.y[j];
						double pred = svm.svm_predict(model, svmNode);
						if (pred > highestPrediction) {
							highestPrediction = pred;
							highestPredictionGold = gold;
						}
						if (gold > highestGold)
							highestGold = gold;

						System.out.printf(
								"Problem %d - gold=%.3f pred = %.3f%n", j,
								gold, pred);
					}
					highestPredictionGoldAvg += highestPredictionGold;
					highestGoldAvg += highestGold;
				}
				highestPredictionGoldAvg /= testProblems.size();
				highestGoldAvg /= testProblems.size();
				System.out.printf("Average F1: %.3f%n",
						highestPredictionGoldAvg);
				System.out.printf("Ideal F1: %.3f%n", highestGoldAvg);

				System.err.printf("Trained %d/%d models.%n", ++count,
						featuresSetsToTest.length);
			}

		}

		for (ModelConfigurationResult mcr : mcrs)
			System.out.printf("%.5f%%\t%.5f%%\t%.5f%%%n",
					mcr.getMacroPrecision() * 100, mcr.getMacroRecall() * 100,
					mcr.getMacroF1() * 100);
		for (ModelConfigurationResult mcr : mcrs)
			System.out.println(mcr.getReadable());

	}

	public static void generateEFModel() throws Exception {

		SmaphConfig.setConfigFile("smaph-config.xml");
		String bingKey = SmaphConfig.getDefaultBingKey();
		String freebKey = SmaphConfig.getDefaultFreebaseKey();
		String freebCache = SmaphConfig.getDefaultFreebaseCache();
		SmaphAnnotator.setCache(SmaphConfig.getDefaultBingCache());
		WikipediaApiInterface wikiApi = new WikipediaApiInterface("wid.cache",
				"redirect.cache");
		FreebaseApi freebApi = new FreebaseApi(freebKey, freebCache);
		double[][] paramsToTest = null;
		double[][] weightsToTest = null;
		Integer[][] featuresSetsToTest = null;

		OptDataset opt = OptDataset.ERD_CHALLENGE;

		if (opt == OptDataset.ERD_CHALLENGE) {
			paramsToTest = new double[][] {
			/*
			 * {0.035, 0.5 }, {0.035, 1 }, {0.035, 4 }, {0.035, 8 }, {0.035, 10
			 * }, {0.035, 16 }, {0.714, .5 }, {0.714, 1 }, {0.714, 4 }, {0.714,
			 * 8 }, {0.714, 10 }, {0.714, 16 }, {0.9, .5 }, {0.9, 1 }, {0.9, 4
			 * }, {0.9, 8 }, {0.9, 10 }, {0.9, 16 },
			 * 
			 * { 1.0/15.0, 1 }, { 1.0/27.0, 1 },
			 */

			
			/*{0.010, 1}, {0.010, 5}, {0.010, 10},
			{0.020, 1}, {0.020, 5}, {0.020, 10},
			{0.030, 1}, {0.030, 5}, {0.030, 10},
			{0.044, 1}, {0.044, 5}, {0.044, 10},
			{0.060, 1}, {0.060, 5}, {0.060, 10},*/
			 
			{ 0.03, 5 }, };
			weightsToTest = new double[][] {

			/*
			 * { 3, 4 }
			 */
			{ 3.8, 4.0 },
			};
			featuresSetsToTest = new Integer[][] {
			{ 1, 2, 3, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
					22, 23, 24, 25, 33, 34, 35, 36, 37 }, };
		} else if (opt == OptDataset.SMAPH_DATASET) {
			paramsToTest = new double[][] {
					{ 0.03, 5 }
			};
			weightsToTest = new double[][] {
					{2.88435, 2.00000}
			};
			featuresSetsToTest = new Integer[][] {
					{7,8,9,10,11,12,13,15,17,20,21,23,24,25,33,34,35,37}
			};
					
		}
			
		int wikiSearckTopK = 10;
		String filePrefix = "_ANW";
		WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");
		List<ModelConfigurationResult> mcrs = new Vector<>();
		for (double boldFilterThr = 0.06; boldFilterThr <= 0.06; boldFilterThr += 0.02) {
			SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
					.getDefaultBingAnnotator(wikiApi, wikiToFreebase,
							boldFilterThr, wikiSearckTopK, bingKey);
			WATAnnotator.setCache("wikisense.cache");

			BinaryExampleGatherer trainEntityFilterGatherer = new BinaryExampleGatherer();
			BinaryExampleGatherer develEntityFilterGatherer = new BinaryExampleGatherer();
			GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
					bingAnnotator, trainEntityFilterGatherer,
					develEntityFilterGatherer, null, null, wikiApi,
					wikiToFreebase, freebApi, opt);

			int count = 0;
			for (Integer[] ftrToTestArray : featuresSetsToTest) {
				for (double[] paramsToTestArray : paramsToTest) {
					double gamma = paramsToTestArray[0];
					double C = paramsToTestArray[1];
					for (double[] weightsPosNeg : weightsToTest) {
						double wPos = weightsPosNeg[0], wNeg = weightsPosNeg[1];
						Vector<Integer> features = new Vector<>(
								Arrays.asList(ftrToTestArray));
						ExampleGatherer trainGatherer = trainEntityFilterGatherer;
						ExampleGatherer develGatherer = develEntityFilterGatherer;
						String fileBase = getModelFileNameBaseEF(
								features.toArray(new Integer[0]), wPos, wNeg,
								boldFilterThr, gamma, C) + filePrefix;

						Triple<svm_problem, double[], double[]> ftrsMinsMaxs = TuneModel
								.getScaledTrainProblem(features, trainGatherer);
						svm_problem trainProblem = ftrsMinsMaxs.getLeft();

						LibSvmUtils.dumpRanges(ftrsMinsMaxs.getMiddle(),
								ftrsMinsMaxs.getRight(), fileBase + ".range");
						svm_parameter param = TuneModel.getParametersEF(wPos,
								wNeg, gamma, C);
						svm_model model = TuneModel.trainModel(param, features,
								trainProblem);
						svm.svm_save_model(fileBase + ".model", model);

						MetricsResultSet metrics = TuneModel.ParameterTester
								.computeMetrics(model, TuneModel
										.getScaledTestProblems(features,
												develGatherer,
												ftrsMinsMaxs.getMiddle(),
												ftrsMinsMaxs.getRight()));

						int tp = metrics.getGlobalTp();
						int fp = metrics.getGlobalFp();
						int fn = metrics.getGlobalFn();
						float microF1 = metrics.getMicroF1();
						float macroF1 = metrics.getMacroF1();
						float macroRec = metrics.getMacroRecall();
						float macroPrec = metrics.getMacroPrecision();
						int totVects = develGatherer.getExamplesCount();
						mcrs.add(new ModelConfigurationResult(features, wPos,
								wNeg, boldFilterThr, tp, fp, fn, totVects - tp
										- fp - fn, microF1, macroF1, macroRec,
								macroPrec));

						System.err.printf("Trained %d/%d models.%n", ++count,
								weightsToTest.length
										* featuresSetsToTest.length
										* paramsToTest.length);
					}
				}
			}
		}
		for (ModelConfigurationResult mcr : mcrs)
			System.out.printf("%.5f%%\t%.5f%%\t%.5f%%%n",
					mcr.getMacroPrecision() * 100, mcr.getMacroRecall() * 100,
					mcr.getMacroF1() * 100);
		for (double[] weightPosNeg : weightsToTest)
			System.out.printf("%.5f\t%.5f%n", weightPosNeg[0], weightPosNeg[1]);
		for (ModelConfigurationResult mcr : mcrs)
			System.out.println(mcr.getReadable());
		for (double[] paramGammaC : paramsToTest)
			System.out.printf("%.5f\t%.5f%n", paramGammaC[0], paramGammaC[1]);

	}

	public static String getModelFileNameBaseLB(Integer[] ftrs,
			double editDistance, double gamma, double C) {
		return String.format("models/model_%s_LB_%.3f_%.8f_%.8f",
				getFtrListRepresentation(ftrs), editDistance, gamma, C);
	}

	public static String getModelFileNameBaseEF(Integer[] ftrs, double wPos,
			double wNeg, double editDistance, double gamma, double C) {
		return String.format("models/model_%s_EF_%.5f_%.5f_%.3f_%.8f_%.8f",
				getFtrListRepresentation(ftrs), wPos, wNeg, editDistance,
				gamma, C);
	}

	private static String getFtrListRepresentation(Integer[] ftrs) {
		Vector<Integer> features = new Vector<Integer>(Arrays.asList(ftrs));
		Collections.sort(features);
		String ftrList = "";
		int i = 0;
		int lastInserted = -1;
		int lastBlockSize = 1;
		while (i < features.size()) {
			int current = features.get(i);
			if (i == 0) // first feature
				ftrList += current;
			else if (current == lastInserted + 1) { // continuation of a block
				if (i == features.size() - 1)// last element, close block
					ftrList += "-" + current;
				lastBlockSize++;
			} else {// start of a new block
				if (lastBlockSize > 1) {
					ftrList += "-" + lastInserted;
				}
				ftrList += "," + current;
				lastBlockSize = 1;
			}
			lastInserted = current;
			i++;
		}
		return ftrList;
	}

}
