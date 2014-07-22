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

import it.acubelab.batframework.metrics.*;
import it.acubelab.batframework.utils.*;
import it.acubelab.batframework.utils.Pair;
import it.acubelab.erd.*;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import libsvm.*;

import org.apache.commons.lang3.tuple.*;

public class TuneModel {
	private static final int THREADS_NUM = 4;

	public enum OptimizaionProfiles {
		MAXIMIZE_TN, MAXIMIZE_MICRO_F1, MAXIMIZE_MACRO_F1
	}

	public static svm_parameter getParameters(double wPos, double wNeg,
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

	public static svm_model trainModel(double wPos, double wNeg,
			Vector<Integer> pickedFtrs, svm_problem trainProblem, double gamma,
			double C) {
		svm_parameter param = getParameters(wPos, wNeg, gamma, C);

		String error_msg = svm.svm_check_parameter(trainProblem, param);

		if (error_msg != null) {
			System.err.print("ERROR: " + error_msg + "\n");
			System.exit(1);
		}

		return svm.svm_train(trainProblem, param);
	}

	public static Triple<svm_problem, double[], double[]> getScaledTrainProblem(
			Vector<Integer> pickedFtrsI, BinaryExampleGatherer gatherer) {
		Collections.sort(pickedFtrsI);

		// find ranges for all features of training set
		Pair<double[], double[]> minsAndMaxs = LibSvmUtils.findRanges(gatherer
				.generateLibSvmProblem());

		double[] mins = minsAndMaxs.first;
		double[] maxs = minsAndMaxs.second;

		// Generate training problem
		svm_problem trainProblem = gatherer.generateLibSvmProblem(pickedFtrsI);
		// Scale training problem
		LibSvmUtils.scaleProblem(trainProblem, mins, maxs);

		return new ImmutableTriple<svm_problem, double[], double[]>(
				trainProblem, mins, maxs);
	}

	public static List<svm_problem> getScaledTestProblems(
			Vector<Integer> pickedFtrsI, BinaryExampleGatherer testGatherer,
			double[] mins, double[] maxs) {
		List<svm_problem> testProblems = testGatherer
				.generateLibSvmProblemOnePerInstance(pickedFtrsI);
		for (svm_problem testProblem : testProblems)
			LibSvmUtils.scaleProblem(testProblem, mins, maxs);
		return testProblems;
	}

	private static Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> trainIterative(
			BinaryExampleGatherer trainGatherer,
			BinaryExampleGatherer develGatherer, double editDistanceThreshold,
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
					editDistanceThreshold, allFtrs, trainGatherer,
					develGatherer, optProfile, globalScoreboard).call();
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
		;
		for (int iteration = 0; iteration < iterations; iteration++) {
			// Do feature selection
			/*
			 * ModelConfigurationResult bestFtr; {
			 * Vector<ModelConfigurationResult> scoreboardFtrSelection = new
			 * Vector<>(); new AblationFeatureSelector(bestwPos, bestwNeg,
			 * editDistanceThreshold, trainGatherer, develGatherer, optProfile,
			 * optProfileThreshold, scoreboardFtrSelection) .run(); bestFtr =
			 * ModelConfigurationResult .findBest(scoreboardFtrSelection,
			 * optProfile, optProfileThreshold);
			 * globalScoreboard.addAll(scoreboardFtrSelection);
			 * System.err.printf("Done feature selection (iteration %d).%n",
			 * iteration); } Vector<Integer> bestFeatures =
			 * bestFtr.getFeatures();
			 */
			Vector<Integer> bestFeatures = allFtrs;
			{ // Fine-tune weights
				Vector<ModelConfigurationResult> scoreboardWeightsTuning = new Vector<>();
				Pair<Double, Double> weights;
				try {
					weights = new WeightSelector(finewPosMin, finewPosMax, -1,
							finewNegMin, finewNegMax, -1, gamma, C, fineSteps,
							editDistanceThreshold, bestFeatures, trainGatherer,
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

	public static void main(String[] args) throws Exception {
		Locale.setDefault(Locale.US);
		String freebKey = "<FREEBASE_KEY>";
		String bingKey = "<BING_KEY>";

		WikipediaApiInterface wikiApi = new WikipediaApiInterface(
				"benchmark/cache/wid.cache", "benchmark/cache/redirect.cache");
		FreebaseApi freebApi = new FreebaseApi(freebKey, "freeb.cache");

		Vector<ModelConfigurationResult> bestEQFModels = new Vector<>();
		Vector<ModelConfigurationResult> bestEFModels = new Vector<>();
		int wikiSearchTopK = 5; // <======== mind this
		double gamma = 1.0;
		double C = 1.0;
		for (double editDistanceThr = 0.7; editDistanceThr <= 0.7; editDistanceThr += 0.1) {
			WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase(
					"mapdb");

			SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
					.getDefaultBingAnnotator(wikiApi, wikiToFreebase,
							editDistanceThr, wikiSearchTopK, bingKey);
			SmaphAnnotator.setCache("bing.cache.full");

			BinaryExampleGatherer trainEntityFilterGatherer = new BinaryExampleGatherer();
			BinaryExampleGatherer develEntityFilterGatherer = new BinaryExampleGatherer();
			GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
					bingAnnotator, trainEntityFilterGatherer,
					develEntityFilterGatherer, wikiApi, wikiToFreebase,
					freebApi);

			SmaphAnnotator.unSetCache();

			Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> modelAndStatsEF = trainIterative(
					trainEntityFilterGatherer, develEntityFilterGatherer,
					editDistanceThr, OptimizaionProfiles.MAXIMIZE_MACRO_F1,
					-1.0, gamma, C);
			/*
			 * Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult>
			 * modelAndStatsEF = trainIterative( trainEmptyQueryGatherer,
			 * develEmptyQueryGatherer, editDistanceThr,
			 * OptimizaionProfiles.MAXIMIZE_TN, 0.02);
			 */
			/*
			 * for (ModelConfigurationResult res : modelAndStatsEQF.first)
			 * System.out.println(res.getReadable());
			 */
			for (ModelConfigurationResult res : modelAndStatsEF.first)
				System.out.println(res.getReadable());

			/* bestEQFModels.add(modelAndStatsEQF.second); */
			bestEFModels.add(modelAndStatsEF.second);
			System.gc();
		}

		for (ModelConfigurationResult modelAndStatsEQF : bestEQFModels)
			System.out.println("Best EQF:" + modelAndStatsEQF.getReadable());
		for (ModelConfigurationResult modelAndStatsEF : bestEFModels)
			System.out.println("Best EF:" + modelAndStatsEF.getReadable());

		System.out.println("Flushing Bing API...");

		SmaphAnnotator.flush();
		wikiApi.flush();
	}

	private static void dumpTrainingData(svm_problem problem) {
		for (int i = 0; i < problem.l; i++) {
			svm_node[] nodes = problem.x[i];
			double value = problem.y[i];
			String nodesStr = "";
			for (svm_node node : nodes)
				nodesStr += String.format("%d:%f ", node.index, node.value);
			System.out.printf("%svalue=%.3f%n", nodesStr, value);
		}

	}

	private static void do_cross_validation(svm_problem prob,
			svm_parameter param) {
		int i;
		int total_correct = 0;
		double total_error = 0;
		double sumv = 0, sumy = 0, sumvv = 0, sumyy = 0, sumvy = 0;
		double[] target = new double[prob.l];

		svm.svm_cross_validation(prob, param, 2, target);
		if (param.svm_type == svm_parameter.EPSILON_SVR
				|| param.svm_type == svm_parameter.NU_SVR) {
			for (i = 0; i < prob.l; i++) {
				double y = prob.y[i];
				double v = target[i];
				total_error += (v - y) * (v - y);
				sumv += v;
				sumy += y;
				sumvv += v * v;
				sumyy += y * y;
				sumvy += v * y;
			}
			System.out.print("Cross Validation Mean squared error = "
					+ total_error / prob.l + "\n");
			System.out
					.print("Cross Validation Squared correlation coefficient = "
							+ ((prob.l * sumvy - sumv * sumy) * (prob.l * sumvy - sumv
									* sumy))
							/ ((prob.l * sumvv - sumv * sumv) * (prob.l * sumyy - sumy
									* sumy)) + "\n");
		} else {
			for (i = 0; i < prob.l; i++)
				if (target[i] == prob.y[i])
					++total_correct;
			System.out.print("Cross Validation Accuracy = " + 100.0
					* total_correct / prob.l + "%\n");
		}
	}

	public static class ParameterTester implements
			Callable<ModelConfigurationResult> {
		private double wPos, wNeg, editDistanceThreshold, gamma, C;
		private BinaryExampleGatherer trainEQFGatherer;
		private BinaryExampleGatherer testGatherer;
		private Vector<Integer> features;
		Vector<ModelConfigurationResult> scoreboard;

		public ParameterTester(double wPos, double wNeg,
				double editDistanceThreshold, Vector<Integer> features,
				BinaryExampleGatherer trainEQFGatherer,
				BinaryExampleGatherer testEQFGatherer,
				OptimizaionProfiles optProfile, double optProfileThreshold,
				double gamma, double C,
				Vector<ModelConfigurationResult> scoreboard) {
			this.wPos = wPos;
			this.wNeg = wNeg;
			this.editDistanceThreshold = editDistanceThreshold;
			this.features = features;
			this.trainEQFGatherer = trainEQFGatherer;
			this.testGatherer = testEQFGatherer;
			Collections.sort(this.features);
			this.scoreboard = scoreboard;
			this.gamma = gamma;
			this.C = C;
		}

		public static MetricsResultSet computeMetrics(svm_model model,
				List<svm_problem> testProblems) throws IOException {
			// Compute metrics
			/*
			 * { int tp = 0, fp = 0, fn = 0, tn = 0; for (int i = 0; i <
			 * testProblem.l; i++) { svm_node[] svmNode = testProblem.x[i];
			 * double gold = testProblem.y[i]; double pred =
			 * svm.svm_predict(model, svmNode); if (gold > 0 && pred > 0) tp++;
			 * if (gold < 0 && pred > 0) fp++; if (gold > 0 && pred < 0) fn++;
			 * if (gold < 0 && pred < 0) tn++; } float f1 =
			 * Metrics.F1(Metrics.recall(tp, fp, fn), Metrics.precision(tp,
			 * fp)); float fnRate = (float) fn / (float) (fn + tp); }
			 */

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

			Triple<svm_problem, double[], double[]> ftrsMinsMaxs = getScaledTrainProblem(
					this.features, trainEQFGatherer);
			double[] mins = ftrsMinsMaxs.getMiddle();
			double[] maxs = ftrsMinsMaxs.getRight();
			svm_problem trainProblem = ftrsMinsMaxs.getLeft();

			svm_model model = trainModel(wPos, wNeg, this.features,
					trainProblem, gamma, C);

			// Generate test problem and scale it.
			List<svm_problem> testProblems = getScaledTestProblems(
					this.features, testGatherer, mins, maxs);

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
		private BinaryExampleGatherer trainEQFGatherer;
		private BinaryExampleGatherer testEQFGatherer;
		private OptimizaionProfiles optProfile;
		private double editDistanceThreshold;
		private double kappaPos, kappaNeg;
		private Vector<Integer> features;
		Vector<ModelConfigurationResult> scoreboard;
		private int steps;

		public WeightSelector(double wPosMin, double wPosMax, double kappaPos,
				double wNegMin, double wNegMax, double kappaNeg, double gamma,
				double C, int steps, double editDistanceThreshold,
				Vector<Integer> features,
				BinaryExampleGatherer trainEQFGatherer,
				BinaryExampleGatherer testEQFGatherer,
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
			this.trainEQFGatherer = trainEQFGatherer;
			this.testEQFGatherer = testEQFGatherer;
			this.optProfile = optProfile;
			this.editDistanceThreshold = editDistanceThreshold;
			this.scoreboard = scoreboard;
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
							editDistanceThreshold, features, trainEQFGatherer,
							testEQFGatherer, optProfile, optProfileThreshold,
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
		private BinaryExampleGatherer trainGatherer;
		private BinaryExampleGatherer testGatherer;
		private OptimizaionProfiles optProfile;
		private double editDistanceThreshold;
		Vector<ModelConfigurationResult> scoreboard;

		public AblationFeatureSelector(double wPos, double wNeg, double gamma,
				double C, double editDistanceThreshold,
				BinaryExampleGatherer trainEQFGatherer,
				BinaryExampleGatherer testEQFGatherer,
				OptimizaionProfiles optProfile, double optProfileThreshold,
				Vector<ModelConfigurationResult> scoreboard) {
			this.wNeg = wNeg;
			this.wPos = wPos;
			this.optProfileThreshold = optProfileThreshold;
			this.trainGatherer = trainEQFGatherer;
			this.testGatherer = testEQFGatherer;
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
		private BinaryExampleGatherer trainEQFGatherer;
		private BinaryExampleGatherer testEQFGatherer;
		private OptimizaionProfiles optProfile;
		private double editDistanceThreshold;
		Vector<ModelConfigurationResult> scoreboard;

		public IncrementalFeatureSelector(double wPos, double wNeg,
				double gamma, double C, double editDistanceThreshold,
				BinaryExampleGatherer trainEQFGatherer,
				BinaryExampleGatherer testEQFGatherer,
				OptimizaionProfiles optProfile, double optProfileThreshold,
				Vector<ModelConfigurationResult> scoreboard) {
			this.wNeg = wNeg;
			this.wPos = wPos;
			this.optProfileThreshold = optProfileThreshold;
			this.trainEQFGatherer = trainEQFGatherer;
			this.testEQFGatherer = testEQFGatherer;
			this.optProfile = optProfile;
			this.editDistanceThreshold = editDistanceThreshold;
			this.scoreboard = scoreboard;
			this.gamma = gamma;
			this.C = C;
		}

		@Override
		public void run() {

			Vector<Integer> ftrToTry = SmaphUtils.getAllFtrVect(testEQFGatherer
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
										pickedFtrsIteration, trainEQFGatherer,
										testEQFGatherer, optProfile,
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