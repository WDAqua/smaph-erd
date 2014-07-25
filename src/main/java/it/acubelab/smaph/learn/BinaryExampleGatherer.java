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

import it.acubelab.smaph.SmaphUtils;

import java.io.*;
import java.util.*;

import libsvm.*;

/**
 * A gatherer of examples for a binary classifier. An example is a pair
 * &lt;features, expected result&gt; generated from an instance, where
 * expected_result is either 'positive' or 'negative'. An instance may generate
 * zero or more pairs. Pairs generated from the same instance form a group.
 */
public class BinaryExampleGatherer {
	private Vector<Vector<double[]>> positiveFeatureVectors = new Vector<>();
	private Vector<Vector<double[]>> negativeFeatureVectors = new Vector<>();
	private int ftrCount = -1;

	/**
	 * Add all examples of an instance, forming a new group.
	 * 
	 * @param posVectors
	 *            feature vectors of the positive examples.
	 * @param negVectors
	 *            feature vectors of the negative examples.
	 */
	public void addExample(Vector<double[]> posVectors,
			Vector<double[]> negVectors) {
		{
			Vector<double[]> mergedFtrVects = new Vector<>();
			mergedFtrVects.addAll(posVectors);
			mergedFtrVects.addAll(negVectors);

			for (double[] ftrVect : mergedFtrVects) {
				if (ftrCount == -1)
					ftrCount = ftrVect.length;
				if (ftrCount != ftrVect.length)
					throw new RuntimeException(
							"Adding feature of a wrong size. ftrCount="
									+ ftrCount + " passed array size="
									+ ftrVect.length);
			}
		}
		positiveFeatureVectors.add(posVectors);
		negativeFeatureVectors.add(negVectors);
	}

	/**
	 * @return a libsvm problem (that is, a list of examples) including all
	 *         features.
	 */
	public svm_problem generateLibSvmProblem() {
		return generateLibSvmProblem(SmaphUtils.getAllFtrVect(this
				.getFtrCount()));
	}

	/**
	 * @param pickedFtrs
	 *            the list of features to pick.
	 * @return a libsvm problem (that is, a list of examples) including only
	 *         features given in pickedFtrs.
	 */
	public svm_problem generateLibSvmProblem(Vector<Integer> pickedFtrs) {
		Vector<Double> targets = new Vector<Double>();
		Vector<svm_node[]> ftrVectors = new Vector<svm_node[]>();
		for (double[] posVect : getPlain(positiveFeatureVectors)) {
			ftrVectors
					.add(LibSvmUtils.featuresArrayToNode(posVect, pickedFtrs));
			targets.add(1.0);
		}
		for (double[] negVect : getPlain(negativeFeatureVectors)) {
			ftrVectors
					.add(LibSvmUtils.featuresArrayToNode(negVect, pickedFtrs));
			targets.add(-1.0);
		}

		svm_problem problem = new svm_problem();
		problem.l = targets.size();
		problem.x = new svm_node[problem.l][];
		for (int i = 0; i < problem.l; i++)
			problem.x[i] = ftrVectors.elementAt(i);
		problem.y = new double[problem.l];
		for (int i = 0; i < problem.l; i++)
			problem.y[i] = targets.elementAt(i);

		return problem;
	}

	/**
	 * @param pickedFtrsI
	 *            the list of features to pick.
	 * @return a list of libsvm problems, one per instance.
	 */
	public List<svm_problem> generateLibSvmProblemOnePerInstance(
			Vector<Integer> pickedFtrsI) {

		Vector<svm_problem> result = new Vector<>();

		for (int i = 0; i < positiveFeatureVectors.size(); i++) {
			Vector<double[]> posFtrVect = positiveFeatureVectors.get(i);
			Vector<double[]> negFtrVect = negativeFeatureVectors.get(i);

			Vector<Double> targets = new Vector<Double>();
			Vector<svm_node[]> ftrVectors = new Vector<svm_node[]>();
			for (double[] posVect : posFtrVect) {
				ftrVectors.add(LibSvmUtils.featuresArrayToNode(posVect,
						pickedFtrsI));
				targets.add(1.0);
			}
			for (double[] negVect : negFtrVect) {
				ftrVectors.add(LibSvmUtils.featuresArrayToNode(negVect,
						pickedFtrsI));
				targets.add(-1.0);
			}

			svm_problem problem = new svm_problem();
			problem.l = targets.size();
			problem.x = new svm_node[problem.l][];
			for (int j = 0; j < problem.l; j++)
				problem.x[j] = ftrVectors.elementAt(j);
			problem.y = new double[problem.l];
			for (int j = 0; j < problem.l; j++)
				problem.y[j] = targets.elementAt(j);
			result.add(problem);
		}
		return result;
	}

	/**
	 * @return the number of examples.
	 */
	public int getExamplesCount() {
		int count = 0;
		for (Vector<double[]> positiveFeatureVector : positiveFeatureVectors)
			count += positiveFeatureVector.size();
		for (Vector<double[]> negativeFeatureVector : negativeFeatureVectors)
			count += negativeFeatureVector.size();

		return count;
	}

	private static Vector<double[]> getPlain(Vector<Vector<double[]>> vectVect) {
		Vector<double[]> res = new Vector<>();
		for (Vector<double[]> vect : vectVect)
			res.addAll(vect);
		return res;
	}

	/**
	 * Dump the examples to a file.
	 * 
	 * @param filename
	 *            where to write the dump.
	 * @throws IOException
	 *             in case of error while writing the file.
	 */
	public void dumpExamplesLibSvm(String filename) throws IOException {
		BufferedWriter wr = new BufferedWriter(new FileWriter(filename, false));

		for (double[] posVect : getPlain(positiveFeatureVectors))
			writeLine(posVect, wr, true);
		for (double[] negVect : getPlain(negativeFeatureVectors))
			writeLine(negVect, wr, false);
		wr.close();
	}

	private void writeLine(double[] ftrVect, BufferedWriter wr, boolean positive)
			throws IOException {
		String line = positive ? "+1 " : "-1 ";
		for (int ftr = 0; ftr < ftrVect.length; ftr++)
			line += String.format("%d:%.9f ", ftr + 1, ftrVect[ftr]);
		wr.write(line + "\n");
	}

	/**
	 * @return the number of features of the gathered examples.
	 */
	public int getFtrCount() {
		return ftrCount;
	}
}
