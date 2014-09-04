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

import it.acubelab.batframework.utils.Pair;
import it.acubelab.smaph.SmaphUtils;

import java.io.*;
import java.util.*;

import libsvm.*;

public class ExampleGatherer {
	private Vector<List<Pair<double[], Double>>> featureVectorsAndGoldGroups = new Vector<>();
	private int ftrCount = -1;

	/**
	 * Add all examples of an instance, forming a new group.
	 * 
	 * @param ftrVectorsAndGoldPairs
	 *            feature vectors of this group, plus their gold value.
	 */
	public void addExample(List<Pair<double[], Double>> ftrVectorsAndGoldPairs) {
		{
			for (Pair<double[], Double> ftrVectAndGoldPair : ftrVectorsAndGoldPairs) {
				double[] ftrVect = ftrVectAndGoldPair.first;
				if (ftrCount == -1)
					ftrCount = ftrVect.length;
				if (ftrCount != ftrVect.length)
					throw new RuntimeException(
							"Adding feature of a wrong size. ftrCount="
									+ ftrCount + " passed array size="
									+ ftrVect.length);
			}
		}
		featureVectorsAndGoldGroups.add(ftrVectorsAndGoldPairs);
	}

	/**
	 * @return a libsvm problem (that is, a list of examples) including all
	 *         features.
	 */
	public svm_problem generateLibSvmProblem() {
		return generateLibSvmProblem(SmaphUtils.getAllFtrVect(this
				.getFtrCount()));
	}

	private svm_problem createProblem(Vector<Double> targets,
			Vector<svm_node[]> ftrVectors) {
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
	 * @param pickedFtrs
	 *            the list of features to pick.
	 * @return a libsvm problem (that is, a list of examples) including only
	 *         features given in pickedFtrs.
	 */
	public svm_problem generateLibSvmProblem(Vector<Integer> pickedFtrs) {
		Vector<Double> targets = new Vector<Double>();
		Vector<svm_node[]> ftrVectors = new Vector<svm_node[]>();
		Vector<Pair<double[], Double>> plainVectors = getPlain(featureVectorsAndGoldGroups);
		for (Pair<double[], Double> vectAndGold : plainVectors) {
			ftrVectors.add(LibSvmUtils.featuresArrayToNode(vectAndGold.first,
					pickedFtrs));
			targets.add(vectAndGold.second);
		}
		return createProblem(targets, ftrVectors);
	}

	/**
	 * @param pickedFtrsI
	 *            the list of features to pick.
	 * @return a list of libsvm problems, one per instance.
	 */
	public List<svm_problem> generateLibSvmProblemOnePerInstance(
			Vector<Integer> pickedFtrs) {

		Vector<svm_problem> result = new Vector<>();

		for (List<Pair<double[], Double>> ftrVectorsAndGolds: featureVectorsAndGoldGroups){
			Vector<Double> targets = new Vector<Double>();
			Vector<svm_node[]> ftrVectors = new Vector<svm_node[]>();
			for (Pair<double[], Double> vectAndGold : ftrVectorsAndGolds) {
				ftrVectors.add(LibSvmUtils.featuresArrayToNode(
						vectAndGold.first, pickedFtrs));
				targets.add(vectAndGold.second);
			}
			result.add(createProblem(targets, ftrVectors));
		}
		return result;
	}

	/**
	 * @return the number of examples.
	 */
	public int getExamplesCount() {
		int count = 0;
		for (List<Pair<double[], Double>> featureVectorAndGold : featureVectorsAndGoldGroups)
			count += featureVectorAndGold.size();
		return count;
	}

	private static Vector<Pair<double[], Double>> getPlain(
			Vector<List<Pair<double[], Double>>> vectVect) {
		Vector<Pair<double[], Double>> res = new Vector<>();
		for (List<Pair<double[], Double>> group : vectVect)
			for (Pair<double[], Double> ftrVectAndGold : group) {
				res.add(new Pair<double[], Double>(ftrVectAndGold.first, ftrVectAndGold.second));
			}
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

		for (Pair<double[], Double> vectAndGold : getPlain(featureVectorsAndGoldGroups))
			writeLine(vectAndGold.first, wr, vectAndGold.second);
		wr.close();
	}

	private void writeLine(double[] ftrVect, BufferedWriter wr, double gold)
			throws IOException {
		String line = String.format("%.5f ", gold);
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
