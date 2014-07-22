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

package it.acubelab.erd.learn;

import it.acubelab.batframework.metrics.MetricsResultSet;
import it.acubelab.batframework.utils.FreebaseApi;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.erd.SmaphAnnotator;
import it.acubelab.erd.SmaphAnnotatorDebugger;
import it.acubelab.tagme.develexp.WikiSenseAnnotatorDevelopment;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.util.*;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_problem;

import org.apache.commons.lang3.tuple.Triple;

public class GenerateModel {

	public static void main(String[] args) throws Exception {
		Locale.setDefault(Locale.US);
		String freebKey = "<FREEBASE_KEY>";
		String bingKey = "<BING_KEY>";
		WikipediaApiInterface wikiApi = new WikipediaApiInterface(
				"benchmark/cache/wid.cache", "benchmark/cache/redirect.cache");
		FreebaseApi freebApi = new FreebaseApi(freebKey, "freeb.cache");
		double[][] paramsToTest = new double[][] {
		/*
		 * {0.035, 0.5 }, {0.035, 1 }, {0.035, 4 }, {0.035, 8 }, {0.035, 10 },
		 * {0.035, 16 }, {0.714, .5 }, {0.714, 1 }, {0.714, 4 }, {0.714, 8 },
		 * {0.714, 10 }, {0.714, 16 }, {0.9, .5 }, {0.9, 1 }, {0.9, 4 }, {0.9, 8
		 * }, {0.9, 10 }, {0.9, 16 },
		 * 
		 * { 1.0/15.0, 1 }, { 1.0/27.0, 1 },
		 */

		/*
		 * {0.01, 1}, {0.01, 5}, {0.01, 10}, {0.03, 1}, {0.03, 5}, {0.03, 10},
		 * {0.044, 1}, {0.044, 5}, {0.044, 10}, {0.06, 1}, {0.06, 5}, {0.06,
		 * 10},
		 */
		{ 0.03, 5 }, };
		double[][] weightsToTest = new double[][] {

		/*
		 * { 3, 4 }
		 */{ 3.8, 6.0 }

		};
		Integer[][] featuresSetsToTest = new Integer[][] { { 1, 2, 3, 6, 7, 8,
				9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
				25 },
		/*
		 * { 1, 2, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18},
		 */

		}; // < -------------------------------------- MIND THIS
		int wikiSearckTopK = 10; // <---------------------------
		String filePrefix = "_ANW";// <---------------------------

		WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");
		List<ModelConfigurationResult> mcrs = new Vector<>();
		for (double editDistanceThr = 0.7; editDistanceThr <= 0.7; editDistanceThr += 0.7) {
			SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
					.getDefaultBingAnnotator(wikiApi, wikiToFreebase,
							editDistanceThr, wikiSearckTopK, bingKey);
			WikiSenseAnnotatorDevelopment.setCache("wikisense.cache");
			SmaphAnnotator.setCache("bing.cache.full");

			BinaryExampleGatherer trainEntityFilterGatherer = new BinaryExampleGatherer();
			BinaryExampleGatherer testEntityFilterGatherer = new BinaryExampleGatherer();
			GenerateTrainingAndTest
					.gatherExamplesTrainingAndDevel(bingAnnotator,
							trainEntityFilterGatherer,
							testEntityFilterGatherer, wikiApi, wikiToFreebase,
							freebApi);

			SmaphAnnotator.unSetCache();
			BinaryExampleGatherer trainGatherer = trainEntityFilterGatherer; // //////////////
																				// <----------------------
			BinaryExampleGatherer testGatherer = testEntityFilterGatherer; // //////////////
																			// <----------------------

			int count = 0;
			for (Integer[] ftrToTestArray : featuresSetsToTest) {
				// double gamma = 1.0 / ftrToTestArray.length; //
				// <--------------------- MIND THIS
				// double C = 1;// < -------------------------------------- MIND
				// THIS
				for (double[] paramsToTestArray : paramsToTest) {
					double gamma = paramsToTestArray[0];
					double C = paramsToTestArray[1];
					for (double[] weightsPosNeg : weightsToTest) {
						double wPos = weightsPosNeg[0], wNeg = weightsPosNeg[1];
						Vector<Integer> features = new Vector<>(
								Arrays.asList(ftrToTestArray));
						Triple<svm_problem, double[], double[]> ftrsMinsMaxs = TuneModel
								.getScaledTrainProblem(features, trainGatherer);
						svm_problem trainProblem = ftrsMinsMaxs.getLeft();

						String fileBase = getModelFileNameBaseEF(
								features.toArray(new Integer[0]), wPos, wNeg,
								editDistanceThr, gamma, C) + filePrefix;
						/*
						 * String fileBase = getModelFileNameBaseEQF(
						 * features.toArray(new Integer[0]), wPos, wNeg);
						 */// < -------------------------
						LibSvmUtils.dumpRanges(ftrsMinsMaxs.getMiddle(),
								ftrsMinsMaxs.getRight(), fileBase + ".range");
						svm_model model = TuneModel.trainModel(wPos, wNeg,
								features, trainProblem, gamma, C);
						svm.svm_save_model(fileBase + ".model", model);

						MetricsResultSet metrics = TuneModel.ParameterTester
								.computeMetrics(model, TuneModel
										.getScaledTestProblems(features,
												testGatherer,
												ftrsMinsMaxs.getMiddle(),
												ftrsMinsMaxs.getRight()));

						int tp = metrics.getGlobalTp();
						int fp = metrics.getGlobalFp();
						int fn = metrics.getGlobalFn();
						float microF1 = metrics.getMicroF1();
						float macroF1 = metrics.getMacroF1();
						float macroRec = metrics.getMacroRecall();
						float macroPrec = metrics.getMacroPrecision();
						int totVects = testGatherer.getExamplesCount();
						mcrs.add(new ModelConfigurationResult(features, wPos,
								wNeg, editDistanceThr, tp, fp, fn, totVects
										- tp - fp - fn, microF1, macroF1,
								macroRec, macroPrec));

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
		WikiSenseAnnotatorDevelopment.flush();
	}

	public static String getModelFileNameBaseEF(Integer[] ftrs, double wPos,
			double wNeg, double editDistance, double gamma, double C) {
		Vector<Integer> features = new Vector<Integer>(Arrays.asList(ftrs));
		Collections.sort(features);
		String filename = "models/model_";
		for (int f : features)
			filename += f + (f == features.get(features.size() - 1) ? "" : ",");
		filename += String.format("_%.5f_%.5f_%.3f_%.8f_%.8f", wPos, wNeg,
				editDistance, gamma, C);
		return filename;

	}

	public static String getModelFileNameBaseEQF(Integer[] ftrs, double wPos,
			double wNeg) {
		Vector<Integer> features = new Vector<Integer>(Arrays.asList(ftrs));
		Collections.sort(features);
		String filename = "models/EQ_model_";
		for (int f : features)
			filename += f + (f == features.get(features.size() - 1) ? "" : ",");
		filename += String.format("_%.5f_%.5f", wPos, wNeg);
		return filename;

	}
}
