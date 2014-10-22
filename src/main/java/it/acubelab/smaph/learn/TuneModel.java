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

import it.acubelab.batframework.metrics.Metrics;
import it.acubelab.batframework.metrics.MetricsResultSet;
import it.acubelab.batframework.utils.FreebaseApi;
import it.acubelab.batframework.utils.Pair;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.smaph.IndexMatch;
import it.acubelab.smaph.SmaphAnnotator;
import it.acubelab.smaph.SmaphConfig;
import it.acubelab.smaph.SmaphUtils;
import it.acubelab.smaph.learn.GenerateTrainingAndTest.OptDataset;
import it.acubelab.smaph.learn.featurePacks.EntityFeaturePack;
import it.acubelab.smaph.learn.normalizer.ScaleFeatureNormalizer;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

public class TuneModel {
	private static final int THREADS_NUM = 4;

	public enum OptimizaionProfiles {
		MAXIMIZE_TN, MAXIMIZE_MICRO_F1, MAXIMIZE_MACRO_F1
	}

	public static svm_parameter getParametersEF(double wPos, double wNeg,
			double gamma, double C) {
		svm_parameter param = new svm_parameter();
		param.svm_type = svm_parameter.C_SVC;
		param.kernel_type = svm_parameter.RBF;
		param.degree = 2;
		param.gamma = gamma;
		param.coef0 = 0;
		param.nu = 0.5;
		param.cache_size = 100;
		param.C = C;
		param.eps = 0.001;
		param.p = 0.1;
		param.shrinking = 1;
		param.probability = 0;
		param.nr_weight = 2;
		param.weight_label = new int[] { 1, -1 };
		param.weight = new double[] { wPos, wNeg };
		return param;
	}
	
	public static svm_parameter getParametersLB(
			double gamma, double c) {
		svm_parameter param = new svm_parameter();
		param.svm_type = svm_parameter.EPSILON_SVR;
		param.kernel_type = svm_parameter.LINEAR;
		param.degree = 2;
		param.gamma = gamma;
		param.coef0 = 0;
		param.nu = 0.5;
		param.cache_size = 100;
		param.C = c;
		param.eps = 0.001;
		param.p = 0.1;
		param.shrinking = 1;
		param.probability = 0;
		param.nr_weight = 2;
		param.weight_label = new int[] { };
		param.weight = new double[] { };
		return param;	}

	public static svm_parameter getParametersEFRegressor(double gamma, double c) {
		
		svm_parameter params = getParametersEF (-1, -1, gamma, c);
		params.svm_type = svm_parameter.EPSILON_SVR;
		return params;
	}



	public static void main(String[] args) throws Exception {
		Locale.setDefault(Locale.US);
		SmaphConfig.setConfigFile("smaph-config.xml");
		String bingKey = SmaphConfig.getDefaultBingKey();
		String freebKey = SmaphConfig.getDefaultFreebaseKey();
		String freebCache = SmaphConfig.getDefaultFreebaseKey();
		SmaphAnnotator.setCache(SmaphConfig.getDefaultBingCache());

		WikipediaApiInterface wikiApi = new WikipediaApiInterface("wid.cache",
				"redirect.cache");
		FreebaseApi freebApi = new FreebaseApi(freebKey, freebCache);

		Vector<ModelConfigurationResult> bestEFModels = new Vector<>();
		double gamma = 0.03;
		double C = 5.0;
		OptDataset opt = OptDataset.SMAPH_DATASET;
		for (double boldFilterThr = 0.06; boldFilterThr <= 0.06; boldFilterThr += 0.1) {
			WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase(
					"mapdb");

			SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
					.getDefaultBingAnnotatorGatherer(wikiApi, 
							boldFilterThr, bingKey);

			ExampleGatherer<EntityFeaturePack> trainEntityFilterGatherer = new ExampleGatherer<EntityFeaturePack>();
			ExampleGatherer<EntityFeaturePack> develEntityFilterGatherer = new ExampleGatherer<EntityFeaturePack>();
			
			GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
					bingAnnotator, trainEntityFilterGatherer,
					develEntityFilterGatherer,null, null ,null, null, null, null, wikiApi, wikiToFreebase,
					freebApi, opt);

			Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> modelAndStatsEF = trainIterative(
					trainEntityFilterGatherer, develEntityFilterGatherer,
					boldFilterThr, OptimizaionProfiles.MAXIMIZE_MACRO_F1, -1.0,
					gamma, C);
			for (ModelConfigurationResult res : modelAndStatsEF.first)
				System.out.println(res.getReadable());

			bestEFModels.add(modelAndStatsEF.second);
			System.gc();
		}

		for (ModelConfigurationResult modelAndStatsEF : bestEFModels)
			System.out.println("Best EF:" + modelAndStatsEF.getReadable());

		System.out.println("Flushing Bing API...");

		SmaphAnnotator.flush();
		wikiApi.flush();
	}

	public static svm_model trainModel(svm_parameter param, 
			Vector<Integer> pickedFtrs, svm_problem trainProblem) {
		String error_msg = svm.svm_check_parameter(trainProblem, param);

		if (error_msg != null) {
			System.err.print("ERROR: " + error_msg + "\n");
			System.exit(1);
		}

		return svm.svm_train(trainProblem, param);
	}

	private static Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> trainIterative(
			ExampleGatherer<EntityFeaturePack> trainGatherer,
			ExampleGatherer<EntityFeaturePack> develGatherer, double boldFilterThreshold,
			OptimizaionProfiles optProfile, double optProfileThreshold,
			double gamma, double C) {

		Vector<ModelConfigurationResult> globalScoreboard = new Vector<>();
		Vector<Integer> allFtrs = SmaphUtils.getAllFtrVect(trainGatherer
				.getFtrCount());
		double bestwPos;
		double bestwNeg;
		double broadwPosMin = 0.1;
		double broadwPosMax = 50.0;
		double broadwNegMin = 1.0;
		double broadwNegMax = 1.0;
		double broadkPos = 0.2;
		int broadSteps = 10;
		int fineSteps = 5;
		int iterations = 3;

		// broad tune weights (all ftr)
		try {
			Pair<Double, Double> bestBroadWeights = new WeightSelector(
					broadwPosMin, broadwPosMax, broadkPos, broadwNegMin,
					broadwNegMax, 1.0, gamma, C, broadSteps,
					boldFilterThreshold, allFtrs, trainGatherer, develGatherer,
					optProfile, globalScoreboard).call();
			bestwPos = bestBroadWeights.first;
			bestwNeg = bestBroadWeights.second;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException();
		}
		System.err.println("Done broad weighting.");

		int bestIterPos = WeightSelector.weightToIter(bestwPos, broadwPosMax,
				broadwPosMin, broadkPos, broadSteps);
		double finewPosMin = WeightSelector.computeWeight(broadwPosMax,
				broadwPosMin, broadkPos, bestIterPos - 1, broadSteps);
		double finewPosMax = WeightSelector.computeWeight(broadwPosMax,
				broadwPosMin, broadkPos, bestIterPos + 1, broadSteps);
		double finewNegMin = 0.5;
		double finewNegMax = 2.0;

		ModelConfigurationResult bestResult = ModelConfigurationResult
				.findBest(globalScoreboard, optProfile, optProfileThreshold);

		for (int iteration = 0; iteration < iterations; iteration++) {
			// Do feature selection
			ModelConfigurationResult bestFtr;
			{
				Vector<ModelConfigurationResult> scoreboardFtrSelection = new Vector<>();
				
				  new AblationFeatureSelector(bestwPos, bestwNeg, gamma, C,
				  boldFilterThreshold, trainGatherer, develGatherer,
				  optProfile, optProfileThreshold, scoreboardFtrSelection)
				  .run();
				 
				/*new IncrementalFeatureSelector(bestwPos, bestwNeg, gamma, C,
						boldFilterThreshold, trainGatherer, develGatherer,
						optProfile, optProfileThreshold, scoreboardFtrSelection)
						.run();*/
				bestFtr = ModelConfigurationResult
						.findBest(scoreboardFtrSelection, optProfile,
								optProfileThreshold);
				globalScoreboard.addAll(scoreboardFtrSelection);
				System.err.printf("Done feature selection (iteration %d).%n",
						iteration);
			}
			Vector<Integer> bestFeatures = bestFtr.getFeatures();

			// Fine-tune weights
			{
				Vector<ModelConfigurationResult> scoreboardWeightsTuning = new Vector<>();
				Pair<Double, Double> weights;
				try {
					weights = new WeightSelector(finewPosMin, finewPosMax, -1,
							finewNegMin, finewNegMax, -1, gamma, C, fineSteps,
							boldFilterThreshold, bestFeatures, trainGatherer,
							develGatherer, optProfile, scoreboardWeightsTuning)
							.call();
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException();
				}
				bestwPos = weights.first;
				bestwNeg = weights.second;
				finewPosMin = bestwPos * 0.5;
				finewPosMax = bestwPos * 2.0;
				finewNegMin = bestwNeg * 0.5;
				finewNegMax = bestwNeg * 2.0;

				globalScoreboard.addAll(scoreboardWeightsTuning);
				System.err.printf("Done weights tuning (iteration %d).%n",
						iteration);
			}
			ModelConfigurationResult newBest = ModelConfigurationResult
					.findBest(globalScoreboard, optProfile, optProfileThreshold);
			if (bestResult != null
					&& newBest.equalResult(bestResult, optProfile,
							optProfileThreshold)) {
				System.err.printf("Not improving, stopping on iteration %d.%n",
						iteration);
				break;
			}
			bestResult = newBest;
		}

		return new Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult>(
				globalScoreboard, ModelConfigurationResult.findBest(
						globalScoreboard, optProfile, optProfileThreshold));

	}

	public static class ParameterTester implements
			Callable<ModelConfigurationResult> {
		private double wPos, wNeg, editDistanceThreshold, gamma, C;
		private ExampleGatherer<EntityFeaturePack> trainGatherer;
		private ExampleGatherer<EntityFeaturePack> testGatherer;
		private Vector<Integer> features;
		Vector<ModelConfigurationResult> scoreboard;

		public ParameterTester(double wPos, double wNeg,
				double editDistanceThreshold, Vector<Integer> features,
				ExampleGatherer<EntityFeaturePack> trainEQFGatherer,
				ExampleGatherer<EntityFeaturePack> testEQFGatherer,
				OptimizaionProfiles optProfile, double optProfileThreshold,
				double gamma, double C,
				Vector<ModelConfigurationResult> scoreboard) {
			this.wPos = wPos;
			this.wNeg = wNeg;
			this.editDistanceThreshold = editDistanceThreshold;
			this.features = features;
			this.trainGatherer = trainEQFGatherer;
			this.testGatherer = testEQFGatherer;
			Collections.sort(this.features);
			this.scoreboard = scoreboard;
			this.gamma = gamma;
			this.C = C;
		}

		public static MetricsResultSet computeMetrics(svm_model model,
				List<svm_problem> testProblems) throws IOException {
			// Compute metrics
			List<HashSet<Integer>> outputOrig = new Vector<>();
			List<HashSet<Integer>> goldStandardOrig = new Vector<>();
			for (svm_problem testProblem : testProblems) {
				HashSet<Integer> goldPairs = new HashSet<>();
				HashSet<Integer> resPairs = new HashSet<>();
				for (int j = 0; j < testProblem.l; j++) {
					svm_node[] svmNode = testProblem.x[j];
					double gold = testProblem.y[j];
					double pred = svm.svm_predict(model, svmNode);
					if (gold > 0.0)
						goldPairs.add(j);
					if (pred > 0.0)
						resPairs.add(j);
				}
				goldStandardOrig.add(goldPairs);
				outputOrig.add(resPairs);
			}

			Metrics<Integer> metrics = new Metrics<>();
			MetricsResultSet results = metrics.getResult(outputOrig,
					goldStandardOrig, new IndexMatch());

			return results;
		}

		@Override
		public ModelConfigurationResult call() throws Exception {

			ScaleFeatureNormalizer scaleFn = new ScaleFeatureNormalizer(trainGatherer);
			svm_problem trainProblem = trainGatherer.generateLibSvmProblem(this.features, scaleFn);

			svm_parameter param = getParametersEF(wPos, wNeg, gamma, C);

			svm_model model = trainModel(param, this.features,
					trainProblem);

			// Generate test problem and scale it.
			List<svm_problem> testProblems = testGatherer.generateLibSvmProblemOnePerInstance(this.features, scaleFn);

			MetricsResultSet metrics = computeMetrics(model, testProblems);

			int tp = metrics.getGlobalTp();
			int fp = metrics.getGlobalFp();
			int fn = metrics.getGlobalFn();
			float microF1 = metrics.getMicroF1();
			float macroF1 = metrics.getMacroF1();
			float macroRec = metrics.getMacroRecall();
			float macroPrec = metrics.getMacroPrecision();

			ModelConfigurationResult mcr = new ModelConfigurationResult(
					features, wPos, wNeg, editDistanceThreshold, tp, fp, fn,
					testGatherer.getExamplesCount() - tp - fp - fn, microF1,
					macroF1, macroRec, macroPrec);

			synchronized (scoreboard) {
				scoreboard.add(mcr);
			}
			return mcr;

		}

	}

	static class WeightSelector implements Callable<Pair<Double, Double>> {
		private double wPosMin, wPosMax, wNegMin, wNegMax, gamma, C;
		private double optProfileThreshold;
		private ExampleGatherer<EntityFeaturePack> trainGatherer;
		private ExampleGatherer<EntityFeaturePack> testGatherer;
		private OptimizaionProfiles optProfile;
		private double boldFilterThreshold;
		private double kappaPos, kappaNeg;
		private Vector<Integer> features;
		Vector<ModelConfigurationResult> scoreboard;
		private int steps;

		public WeightSelector(double wPosMin, double wPosMax, double kappaPos,
				double wNegMin, double wNegMax, double kappaNeg, double gamma,
				double C, int steps, double boldFilterThreshold,
				Vector<Integer> features,
				ExampleGatherer<EntityFeaturePack> trainEQFGatherer,
				ExampleGatherer<EntityFeaturePack> testEQFGatherer,
				OptimizaionProfiles optProfile,
				Vector<ModelConfigurationResult> scoreboard) {
			if (kappaNeg == -1)
				kappaNeg = (wNegMax - wNegMin) / steps;
			if (kappaPos == -1)
				kappaPos = (wPosMax - wPosMin) / steps;

			if (!(kappaPos > 0 && (wPosMax - wPosMin == 0 || kappaPos <= wPosMax
					- wPosMin)))
				throw new IllegalArgumentException(String.format(
						"k must be between 0.0 and %f. Got %f", wPosMax
								- wPosMin, kappaPos));
			if (!(kappaNeg > 0 && (wNegMax - wNegMin == 0 || kappaNeg <= wNegMax
					- wNegMin)))
				throw new IllegalArgumentException(String.format(
						"k must be between 0.0 and %f. Got %f", wNegMax
								- wNegMin, kappaNeg));
			this.wNegMin = wNegMin;
			this.wNegMax = wNegMax;
			this.wPosMin = wPosMin;
			this.wPosMax = wPosMax;
			this.kappaNeg = kappaNeg;
			this.kappaPos = kappaPos;
			this.features = features;
			this.trainGatherer = trainEQFGatherer;
			this.testGatherer = testEQFGatherer;
			this.optProfile = optProfile;
			this.boldFilterThreshold = boldFilterThreshold;
			this.scoreboard = scoreboard;
			this.C = C;
			this.gamma = gamma;
			this.steps = steps;
		}

		public static double computeWeight(double wMax, double wMin,
				double kappa, int iteration, int steps) {
			if (iteration < 0)
				return wMin;
			double exp = wMax == wMin ? 1 : Math.log((wMax - wMin) / kappa)
					/ Math.log(steps);

			return wMin + kappa * Math.pow(iteration, exp);
		}

		public static int weightToIter(double weight, double wMax, double wMin,
				double kappa, int steps) {
			if (wMax == wMin)
				return 0;
			double exp = Math.log((wMax - wMin) / kappa) / Math.log(steps);

			return (int) Math.round(Math
					.pow((weight - wMin) / kappa, 1.0 / exp));
		}

		@Override
		public Pair<Double, Double> call() throws Exception {
			ExecutorService execServ = Executors
					.newFixedThreadPool(THREADS_NUM);
			List<Future<ModelConfigurationResult>> futures = new Vector<>();

			double wPos, wNeg;
			for (int posI = 0; (wPos = computeWeight(wPosMax, wPosMin,
					kappaPos, posI, steps)) <= wPosMax; posI++)
				for (int negI = 0; (wNeg = computeWeight(wNegMax, wNegMin,
						kappaNeg, negI, steps)) <= wNegMax; negI++)
					futures.add(execServ.submit(new ParameterTester(wPos, wNeg,
							boldFilterThreshold, features, trainGatherer,
							testGatherer, optProfile, optProfileThreshold,
							gamma, C, scoreboard)));

			ModelConfigurationResult best = null;
			for (Future<ModelConfigurationResult> future : futures)
				try {
					ModelConfigurationResult res = future.get();
					if (best == null
							|| best.worseThan(res, optProfile,
									optProfileThreshold))
						best = res;
				} catch (InterruptedException | ExecutionException | Error e) {
					throw new RuntimeException(e);
				}
			execServ.shutdown();

			return new Pair<Double, Double>(best.getWPos(), best.getWNeg());
		}

	}

	static class AblationFeatureSelector implements Runnable {
		private double wPos, wNeg, gamma, C;
		private double optProfileThreshold;
		private ExampleGatherer<EntityFeaturePack> trainGatherer;
		private ExampleGatherer<EntityFeaturePack> testGatherer;
		private OptimizaionProfiles optProfile;
		private double editDistanceThreshold;
		Vector<ModelConfigurationResult> scoreboard;

		public AblationFeatureSelector(double wPos, double wNeg, double gamma,
				double C, double editDistanceThreshold,
				ExampleGatherer<EntityFeaturePack> trainGatherer,
				ExampleGatherer<EntityFeaturePack> testGatherer,
				OptimizaionProfiles optProfile, double optProfileThreshold,
				Vector<ModelConfigurationResult> scoreboard) {
			this.wNeg = wNeg;
			this.wPos = wPos;
			this.optProfileThreshold = optProfileThreshold;
			this.trainGatherer = trainGatherer;
			this.testGatherer = testGatherer;
			this.optProfile = optProfile;
			this.editDistanceThreshold = editDistanceThreshold;
			this.scoreboard = scoreboard;
			this.gamma = gamma;
			this.C = C;
		}

		@Override
		public void run() {

			ModelConfigurationResult bestBase;
			try {
				bestBase = new ParameterTester(wPos, wNeg,
						editDistanceThreshold,
						SmaphUtils.getAllFtrVect(testGatherer.getFtrCount()),
						trainGatherer, testGatherer, optProfile,
						optProfileThreshold, gamma, C, scoreboard).call();
			} catch (Exception e1) {
				e1.printStackTrace();
				throw new RuntimeException(e1);
			}

			while (bestBase.getFeatures().size() > 1) {
				ExecutorService execServ = Executors
						.newFixedThreadPool(THREADS_NUM);
				List<Future<ModelConfigurationResult>> futures = new Vector<>();
				HashMap<Future<ModelConfigurationResult>, Integer> futureToFtrId = new HashMap<>();

				for (int testFtrId : bestBase.getFeatures()) {
					Vector<Integer> pickedFtrsIteration = new Vector<>(
							bestBase.getFeatures());
					pickedFtrsIteration.remove(pickedFtrsIteration
							.indexOf(testFtrId));

					try {
						Future<ModelConfigurationResult> future = execServ
								.submit(new ParameterTester(wPos, wNeg,
										editDistanceThreshold,
										pickedFtrsIteration, trainGatherer,
										testGatherer, optProfile,
										optProfileThreshold, gamma, C,
										scoreboard));
						futures.add(future);
						futureToFtrId.put(future, testFtrId);

					} catch (Exception | Error e) {
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				}

				ModelConfigurationResult bestIter = null;
				for (Future<ModelConfigurationResult> future : futures)
					try {
						ModelConfigurationResult res = future.get();
						if (bestIter == null
								|| bestIter.worseThan(res, optProfile,
										optProfileThreshold))
							bestIter = res;
					} catch (InterruptedException | ExecutionException | Error e) {
						throw new RuntimeException(e);
					}
				execServ.shutdown();

				if (bestIter.worseThan(bestBase, optProfile,
						optProfileThreshold))
					break;
				else
					bestBase = bestIter;
			}
		}
	}

	static class IncrementalFeatureSelector implements Runnable {
		private double wPos, wNeg, gamma, C;
		private double optProfileThreshold;
		private ExampleGatherer<EntityFeaturePack> trainGatherer;
		private ExampleGatherer<EntityFeaturePack> testGatherer;
		private OptimizaionProfiles optProfile;
		private double editDistanceThreshold;
		Vector<ModelConfigurationResult> scoreboard;

		public IncrementalFeatureSelector(double wPos, double wNeg,
				double gamma, double C, double editDistanceThreshold,
				ExampleGatherer<EntityFeaturePack> trainGatherer,
				ExampleGatherer<EntityFeaturePack> testGatherer,
				OptimizaionProfiles optProfile, double optProfileThreshold,
				Vector<ModelConfigurationResult> scoreboard) {
			this.wNeg = wNeg;
			this.wPos = wPos;
			this.optProfileThreshold = optProfileThreshold;
			this.trainGatherer = trainGatherer;
			this.testGatherer = testGatherer;
			this.optProfile = optProfile;
			this.editDistanceThreshold = editDistanceThreshold;
			this.scoreboard = scoreboard;
			this.gamma = gamma;
			this.C = C;
		}

		@Override
		public void run() {

			Vector<Integer> ftrToTry = SmaphUtils.getAllFtrVect(testGatherer
					.getFtrCount());

			ModelConfigurationResult bestBase = null;
			while (!ftrToTry.isEmpty()) {
				ModelConfigurationResult bestIter = bestBase;
				ExecutorService execServ = Executors
						.newFixedThreadPool(THREADS_NUM);
				List<Future<ModelConfigurationResult>> futures = new Vector<>();
				HashMap<Future<ModelConfigurationResult>, Integer> futureToFtrId = new HashMap<>();

				for (int testFtrId : ftrToTry) {
					Vector<Integer> pickedFtrsIteration = new Vector<>(
							bestBase == null ? new Vector<Integer>()
									: bestBase.getFeatures());
					pickedFtrsIteration.add(testFtrId);

					try {
						Future<ModelConfigurationResult> future = execServ
								.submit(new ParameterTester(wPos, wNeg,
										editDistanceThreshold,
										pickedFtrsIteration, trainGatherer,
										testGatherer, optProfile,
										optProfileThreshold, gamma, C,
										scoreboard));
						futures.add(future);
						futureToFtrId.put(future, testFtrId);

					} catch (Exception | Error e) {
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				}

				int bestFtrId = -1;
				for (Future<ModelConfigurationResult> future : futures)
					try {
						ModelConfigurationResult res = future.get();
						if (bestIter == null
								|| bestIter.worseThan(res, optProfile,
										optProfileThreshold)) {
							bestFtrId = futureToFtrId.get(future);
							bestIter = res;
						}
					} catch (InterruptedException | ExecutionException | Error e) {
						throw new RuntimeException(e);
					}
				execServ.shutdown();

				if (bestFtrId == -1) {
					break;
				} else {
					bestBase = bestIter;
					ftrToTry.remove(ftrToTry.indexOf(bestFtrId));
				}

			}
		}
	}

}
