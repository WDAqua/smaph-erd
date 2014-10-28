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
import it.acubelab.batframework.metrics.MetricsResultSet;
import it.acubelab.batframework.systemPlugins.WATAnnotator;
import it.acubelab.batframework.utils.FreebaseApi;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.smaph.SmaphAnnotator;
import it.acubelab.smaph.SmaphConfig;
import it.acubelab.smaph.SmaphUtils;
import it.acubelab.smaph.learn.GenerateTrainingAndTest.OptDataset;
import it.acubelab.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.acubelab.smaph.learn.featurePacks.BindingFeaturePack;
import it.acubelab.smaph.learn.featurePacks.EntityFeaturePack;
import it.acubelab.smaph.learn.featurePacks.FeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;
import it.acubelab.smaph.learn.normalizer.NoFeatureNormalizer;
import it.acubelab.smaph.learn.normalizer.ScaleFeatureNormalizer;
import it.acubelab.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import it.acubelab.smaph.linkback.annotationRegressor.AnnotationRegressor;
import it.acubelab.smaph.linkback.annotationRegressor.LibLinearAnnotationRegressor;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

	import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

	import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

	public class GenerateModel {
		public static void main(String[] args) throws Exception {
			Locale.setDefault(Locale.US);
			generateEFModel(OptDataset.SMAPH_DATASET);
			//generateAnnotationModel();
			//generateLBModel();
			WATAnnotator.flush();
		}

		public static void generateAnnotationModel() throws Exception {

			SmaphConfig.setConfigFile("smaph-config.xml");
			String bingKey = SmaphConfig.getDefaultBingKey();
			String freebKey = SmaphConfig.getDefaultFreebaseKey();
			String freebCache = SmaphConfig.getDefaultFreebaseCache();
			SmaphAnnotator.setCache(SmaphConfig.getDefaultBingCache());
			WikipediaApiInterface wikiApi = new WikipediaApiInterface("wid.cache",
					"redirect.cache");
			FreebaseApi freebApi = new FreebaseApi(freebKey, freebCache);
			int[][] featuresSetsToTest = new int[][] { SmaphUtils
					.getAllFtrVect(new AnnotationFeaturePack().getFeatureCount()) };
			OptDataset opt = OptDataset.SMAPH_DATASET;
			
			String filePrefix = "_ANW";
			WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");
			List<ModelConfigurationResult> mcrs = new Vector<>();
			for (double boldFilterThr = 0.06; boldFilterThr <= 0.06; boldFilterThr += 0.02) {
				SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
						.getDefaultBingAnnotatorGatherer(wikiApi, 
								boldFilterThr, bingKey);
				WATAnnotator.setCache("wikisense.cache");

				ExampleGatherer<Annotation> trainAnnotationGatherer = new ExampleGatherer<Annotation>();
				ExampleGatherer<Annotation> develAnnotationGatherer = new ExampleGatherer<Annotation>();
				GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
						bingAnnotator, null, null, null,
						null, trainAnnotationGatherer, develAnnotationGatherer,null,null,  wikiApi, wikiToFreebase, freebApi, opt);
				
				ZScoreFeatureNormalizer fn = new ZScoreFeatureNormalizer(trainAnnotationGatherer);
				fn.dump("/tmp/train_ann.dat.zscore", new AnnotationFeaturePack());
				
				System.out.println("Dumping Annotation training problems...");
				trainAnnotationGatherer.dumpExamplesLibSvm("train_ann_scaled.dat", fn);
				System.out.println("Dumping Annotation training problems for ranking...");
				trainAnnotationGatherer.dumpExamplesRankLib("train_ann_scaled_ranking.dat", fn);
				System.out.println("Dumping Annotation devel problems...");
				develAnnotationGatherer.dumpExamplesLibSvm("devel_ann_scaled.dat", fn);
				System.out.println("Dumping Annotation devel problems for ranking...");
				develAnnotationGatherer.dumpExamplesRankLib("devel_ann_scaled_ranking.dat", fn);
			}
			for (ModelConfigurationResult mcr : mcrs)
				System.out.printf("%.5f%%\t%.5f%%\t%.5f%%%n",
						mcr.getMacroPrecision() * 100, mcr.getMacroRecall() * 100,
						mcr.getMacroF1() * 100);
			for (ModelConfigurationResult mcr : mcrs)
				System.out.println(mcr.getReadable());

		}


		public static void generateLBModel() throws Exception {
			String AFmodel = "/tmp/train_ann_scaled.dat.model";
			String AFrange = "/tmp/train_ann.dat.zscore";
			
			AnnotationRegressor ar = new LibLinearAnnotationRegressor(AFmodel);
			ZScoreFeatureNormalizer annFn = new ZScoreFeatureNormalizer(AFrange, new AnnotationFeaturePack());
			
			SmaphConfig.setConfigFile("smaph-config.xml");
			String bingKey = SmaphConfig.getDefaultBingKey();
			String freebKey = SmaphConfig.getDefaultFreebaseKey();
			String freebCache = SmaphConfig.getDefaultFreebaseCache();
			SmaphAnnotator.setCache(SmaphConfig.getDefaultBingCache());
			WikipediaApiInterface wikiApi = new WikipediaApiInterface("wid.cache",
					"redirect.cache");
			FreebaseApi freebApi = new FreebaseApi(freebKey, freebCache);
			int[][] featuresSetsToTest = new int[][] { SmaphUtils
					.getAllFtrVect(new BindingFeaturePack().getFeatureCount()) };
			OptDataset opt = OptDataset.SMAPH_DATASET;
			
			String filePrefix = "_ANW";
			WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");
			List<ModelConfigurationResult> mcrs = new Vector<>();
			for (double boldFilterThr = 0.06; boldFilterThr <= 0.06; boldFilterThr += 0.02) {
				SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
						.getDefaultBingAnnotatorGatherer(wikiApi, 
								boldFilterThr, bingKey);
				WATAnnotator.setCache("wikisense.cache");

				ExampleGatherer<HashSet<Annotation>> trainLinkBackGatherer = new ExampleGatherer<HashSet<Annotation>>();
				ExampleGatherer<HashSet<Annotation>> develLinkBackGatherer = new ExampleGatherer<HashSet<Annotation>>();
				GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
						bingAnnotator, null, null, trainLinkBackGatherer,
						develLinkBackGatherer, null, null, ar, annFn, wikiApi, wikiToFreebase, freebApi, opt);
				
				FeatureNormalizer fn = new NoFeatureNormalizer();
				System.out.println("Dumping binding training problems...");
				trainLinkBackGatherer.dumpExamplesLibSvm("train_binding.dat", fn);
				System.out.println("Dumping binding training problems for ranking...");
				trainLinkBackGatherer.dumpExamplesRankLib("train_binding_ranking.dat", fn);
				/*System.out.println("Dumping LB development problems...");
				develLinkBackGatherer.dumpExamplesLibSvm("devel.dat", fn);*/

				System.exit(0);
				
				System.out.println("Training models...");
				int count = 0;
				for (int[] ftrToTestArray : featuresSetsToTest) {
					double gamma = 1.0 / ftrToTestArray.length;
					double C = 5.0;
					String fileBase = getModelFileNameBaseLB(
							ftrToTestArray, boldFilterThr, gamma,
							C) + filePrefix;

					svm_problem trainProblem = trainLinkBackGatherer.generateLibSvmProblem(ftrToTestArray, fn);

					System.out.println("Dumping LB ranges...");
					svm_parameter param = TuneModel.getParametersLB(gamma, C);
					System.out.println("Training LB model...");
					svm_model model = TuneModel.trainModel(param, trainProblem);
					svm.svm_save_model(fileBase + ".model", model);
					List<svm_problem> testProblems = develLinkBackGatherer.generateLibSvmProblemOnePerInstance(ftrToTestArray, fn);
					double highestPredictionGoldAvg = 0.0;
					double highestGoldAvg = 0.0;
					
					//Vector<Double> preds = new Vector<>();
					for (svm_problem testProblem : testProblems) {
						double highestPrediction = Double.NEGATIVE_INFINITY;
						double highestPredictionGold = Double.NEGATIVE_INFINITY;
						double highestGold = Double.NEGATIVE_INFINITY;
						int highestPredictionId = -1;
						for (int j = 0; j < testProblem.l; j++) {
							svm_node[] svmNode = testProblem.x[j];
							double gold = testProblem.y[j];
							double pred = svm.svm_predict(model, svmNode);
							//System.out.printf("Binding %d - pred=%.3f gold=%.3f%n", j, pred, gold);

							//preds.add(pred);
							if (pred > highestPrediction) {
								highestPrediction = pred;
								highestPredictionGold = gold;
								highestPredictionId = j;
							}
							if (gold > highestGold)
								highestGold = gold;

						}
						System.out.printf(
									"Best binding is %d - upper_bound_2=%.3f highest_pred=%.3f%n", highestPredictionId, highestGold, highestPredictionGold);

						highestPredictionGoldAvg += highestPredictionGold;
						highestGoldAvg += highestGold;
					}
					highestPredictionGoldAvg /= testProblems.size();
					highestGoldAvg /= testProblems.size();
					System.out.printf("Average F1: %.3f%n",
							highestPredictionGoldAvg);
					System.out.printf("Avg. Upper bound 2: %.3f%n", highestGoldAvg);

					System.err.printf("Trained %d/%d models.%n", ++count,
							featuresSetsToTest.length);
					
					
					/*for (double i = -50; i<60; i+=0.2){
						int countI =0;
						for (double s : preds)
							if (i<s && s<=i+0.2)
								countI ++;
						System.out.printf("%.3f-%.3f : %d (%.2f%%)%n", i, i+0.2, countI, ((double)countI)*100.0/preds.size());
								
					}*/
				}

			}

			for (ModelConfigurationResult mcr : mcrs)
				System.out.printf("%.5f%%\t%.5f%%\t%.5f%%%n",
						mcr.getMacroPrecision() * 100, mcr.getMacroRecall() * 100,
						mcr.getMacroF1() * 100);
			for (ModelConfigurationResult mcr : mcrs)
				System.out.println(mcr.getReadable());

		}
		public static void generateEFModel(OptDataset opt) throws Exception {

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
			int[][] featuresSetsToTest = null;

		if (opt == OptDataset.ERD_CHALLENGE) {
			paramsToTest = new double[][] { { 0.010, 100 } };
			weightsToTest = new double[][] { { 3.8, 5.2 }, };
			featuresSetsToTest = new int[][] {
					{ 1, 2, 3, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 33, 34, 35, 36, 37 },
					};
		} else if (opt == OptDataset.SMAPH_DATASET) {
			paramsToTest = new double[][] {
					{ 0.050, 1 },

			};
			weightsToTest = new double[][] {
					{2.88435, 1.2},
					{2.88435, 1.4},
					{2.88435, 1.6},
					{2.88435, 1.8},
					{2.88435, 2.0},
					{2.88435, 2.2},
			};
			featuresSetsToTest = new int[][] {
					//{7,8,9,10,11,12,13,15,17,20,21,23,24,25,33,34,35,37},
					//{ 1, 2, 3, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 33, 34, 35, 36, 37,38,39,40,41 },
					{ 2, 3, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 33, 34, 35, 36, 37,39,40,41,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66},
					
			};
					
		}
			
		String filePrefix = "_ANW"+(opt == OptDataset.ERD_CHALLENGE?"-erd":"-smaph");
		WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");
		List<ModelConfigurationResult> mcrs = new Vector<>();
		for (double boldFilterThr = 0.06; boldFilterThr <= 0.06; boldFilterThr += 0.02) {
			SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
					.getDefaultBingAnnotatorGatherer(wikiApi,
							boldFilterThr, bingKey);
			WATAnnotator.setCache("wikisense.cache");

			ExampleGatherer<Tag> trainEntityFilterGatherer = new ExampleGatherer<Tag>();
			ExampleGatherer<Tag> develEntityFilterGatherer = new ExampleGatherer<Tag>();
			GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
					bingAnnotator, trainEntityFilterGatherer,
					develEntityFilterGatherer, null, null, null, null,null, null, wikiApi,
					wikiToFreebase, freebApi, opt);
			//ScaleFeatureNormalizer fNorm = new ScaleFeatureNormalizer(trainEntityFilterGatherer);
			//trainEntityFilterGatherer.dumpExamplesLibSvm("train_ef_scaled.dat", fNorm);
			ZScoreFeatureNormalizer fNorm = new ZScoreFeatureNormalizer(trainEntityFilterGatherer);
			trainEntityFilterGatherer.dumpExamplesLibSvm("train_ef_zscore.dat", fNorm);
			trainEntityFilterGatherer.dumpExamplesLibSvm("train_ef.dat", new NoFeatureNormalizer());
			
			int count = 0;
			for (int[] ftrToTestArray : featuresSetsToTest) {
				for (double[] paramsToTestArray : paramsToTest) {
					double gamma = paramsToTestArray[0];
					double C = paramsToTestArray[1];
					for (double[] weightsPosNeg : weightsToTest) {
						double wPos = weightsPosNeg[0], wNeg = weightsPosNeg[1];
						ExampleGatherer<Tag> trainGatherer = trainEntityFilterGatherer;
						ExampleGatherer<Tag> develGatherer = develEntityFilterGatherer;
						String fileBase = getModelFileNameBaseEF(
								ftrToTestArray, wPos, wNeg,
								boldFilterThr, gamma, C) + filePrefix;

						svm_problem trainProblem = trainGatherer.generateLibSvmProblem(ftrToTestArray, fNorm);

						svm_parameter param = TuneModel.getParametersEF(wPos,
								wNeg, gamma, C);
						
						System.out.println("Training binary classifier...");
						svm_model model = TuneModel.trainModel(param,
								trainProblem);
						svm.svm_save_model(fileBase + ".model", model);
						
						/*svm_parameter paramRegr = TuneModel.getParametersEFRegressor(gamma, C);
						System.out.println("Training regressor...");
						svm_model modelRegr = TuneModel.trainModel(paramRegr, features,
								trainProblem);
						String fileBaseRegr = fileBase +".regressor";
						svm.svm_save_model(fileBaseRegr + ".model", modelRegr);*/
						//fNorm.dump(fileBase + ".range", new EntityFeaturePack());
						fNorm.dump(fileBase + ".zscore", new EntityFeaturePack());
						

						//	TODO: pass SvmModel rather than svm_model
						MetricsResultSet metrics = TuneModel.ParameterTester
								.computeMetrics(model, develGatherer.generateLibSvmProblemOnePerInstance(ftrToTestArray, fNorm));

						int tp = metrics.getGlobalTp();
						int fp = metrics.getGlobalFp();
						int fn = metrics.getGlobalFn();
						float microF1 = metrics.getMicroF1();
						float macroF1 = metrics.getMacroF1();
						float macroRec = metrics.getMacroRecall();
						float macroPrec = metrics.getMacroPrecision();
						int totVects = develGatherer.getExamplesCount();
						mcrs.add(new ModelConfigurationResult(ftrToTestArray, wPos,
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
			System.out.printf("P/R/F1 %.5f%%\t%.5f%%\t%.5f%% TP/FP/FN: %d/%d/%d%n",
					mcr.getMacroPrecision() * 100, mcr.getMacroRecall() * 100,
					mcr.getMacroF1() * 100, mcr.getTP(), mcr.getFP(), mcr.getFN());
		for (double[] weightPosNeg : weightsToTest)
			System.out.printf("%.5f\t%.5f%n", weightPosNeg[0], weightPosNeg[1]);
		for (ModelConfigurationResult mcr : mcrs)
			System.out.println(mcr.getReadable());
		for (double[] paramGammaC : paramsToTest)
			System.out.printf("%.5f\t%.5f%n", paramGammaC[0], paramGammaC[1]);

	}

	public static String getModelFileNameBaseLB(int[] ftrs,
			double editDistance, double gamma, double C) {
		return String.format("models/model_%s_LB_%.3f_%.8f_%.8f",
				getFtrListRepresentation(ftrs), editDistance, gamma, C);
	}

	public static String getModelFileNameBaseEF(int[] ftrs, double wPos,
			double wNeg, double editDistance, double gamma, double C) {
		return String.format("models/model_%s_EF_%.5f_%.5f_%.3f_%.8f_%.8f",
				getFtrListRepresentation(ftrs), wPos, wNeg, editDistance, gamma, C);
	}
	
	private static String getModelFileNameBaseAF(int[] ftrs,
			double boldFilterThr, double gamma, double c) {
		return String.format("models/model_%s_AF_%.3f_%.8f_%.8f",
				getFtrListRepresentation(ftrs), boldFilterThr, gamma, c);
	}

	private static String getFtrListRepresentation(int[] ftrs) {
		Arrays.sort(ftrs);
		String ftrList = "";
		int i = 0;
		int lastInserted = -1;
		int lastBlockSize = 1;
		while (i < ftrs.length) {
			int current = ftrs[i];
			if (i == 0) // first feature
				ftrList += current;
			else if (current == lastInserted + 1) { // continuation of a block
				if (i == ftrs.length - 1)// last element, close block
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
