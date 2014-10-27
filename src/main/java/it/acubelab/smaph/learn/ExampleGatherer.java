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
import it.acubelab.smaph.learn.featurePacks.FeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;

import java.io.*;
import java.util.*;

import libsvm.*;

public class ExampleGatherer <T extends FeaturePack> {
	private Vector<List<Pair<T, Double>>> featureVectorsAndGoldGroups = new Vector<>();
	private int ftrCount = -1;

	/**
	 * Add all examples of an instance, forming a new group.
	 * 
	 * @param list
	 *            normalized feature vectors of this group, plus their gold value.
	 */
	public void addExample(List<Pair<T, Double>> list) {
		featureVectorsAndGoldGroups.add(list);
		if (ftrCount < 0)
			for (Pair<T, Double> p : list)
				ftrCount = p.first.getFeatureCount();
	}
	
	/**
	 * @return the number of examples.
	 */
	public int getExamplesCount() {
		int count = 0;
		for (List<Pair<T, Double>> featureVectorAndGold : featureVectorsAndGoldGroups)
			count += featureVectorAndGold.size();
		return count;
	}
	
	public List<T> getAllFeaturePacks(){
		List<T> ftrPacks = new Vector<T>();
		for (List<Pair<T, Double>> featureVectorsAndGoldGroup: featureVectorsAndGoldGroups)
			for (Pair<T, Double>featureVectorsAndGold : featureVectorsAndGoldGroup)
				ftrPacks.add(featureVectorsAndGold.first);
		return ftrPacks;
	}

	private static <T extends FeaturePack> Vector<Pair<T, Double>> getPlain(
			Vector<List<Pair<T, Double>>> vectVect) {
		Vector<Pair<T, Double>> res = new Vector<>();
		for (List<Pair<T, Double>> group : vectVect)
			for (Pair<T, Double> ftrVectAndGold : group) {
				res.add(new Pair<T, Double>(ftrVectAndGold.first, ftrVectAndGold.second));
			}
		return res;
	}

	
	/**
	 * @return the number of features of the gathered examples.
	 */
	public int getFtrCount() {
		return ftrCount;
	}

	
	/**
	 * @return a libsvm problem (that is, a list of examples) including all
	 *         features.
	 */
	public svm_problem generateLibSvmProblem(FeatureNormalizer fn) {
		return generateLibSvmProblem(SmaphUtils.getAllFtrVect(this
				.getFtrCount()), fn);
	}

	/**
	 * @param pickedFtrs
	 *            the list of features to pick.
	 * @return a libsvm problem (that is, a list of examples) including only
	 *         features given in pickedFtrs.
	 */
	public svm_problem generateLibSvmProblem(int[] pickedFtrs, FeatureNormalizer fn) {
		Vector<Double> targets = new Vector<Double>();
		Vector<svm_node[]> ftrVectors = new Vector<svm_node[]>();
		Vector<Pair<T, Double>> plainVectors = getPlain(featureVectorsAndGoldGroups);
		for (Pair<T, Double> vectAndGold : plainVectors) {
			ftrVectors.add(LibSvmModel.featuresArrayToNode(fn.ftrToNormalizedFtrArray(vectAndGold.first),
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
			int[] pickedFtrs, FeatureNormalizer fn) {

		Vector<svm_problem> result = new Vector<>();

		for (List<Pair<T, Double>> ftrVectorsAndGolds: featureVectorsAndGoldGroups){
			Vector<Double> targets = new Vector<Double>();
			Vector<svm_node[]> ftrVectors = new Vector<svm_node[]>();
			for (Pair<T, Double> vectAndGold : ftrVectorsAndGolds) {
				ftrVectors.add(LibSvmModel.featuresArrayToNode(
						fn.ftrToNormalizedFtrArray(vectAndGold.first), pickedFtrs));
				targets.add(vectAndGold.second);
			}
			result.add(createProblem(targets, ftrVectors));
		}
		return result;
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
	 * Dump the examples to a file.
	 * 
	 * @param filename
	 *            where to write the dump.
	 * @throws IOException
	 *             in case of error while writing the file.
	 */
	public void dumpExamplesLibSvm(String filename, FeatureNormalizer fn) throws IOException {
		BufferedWriter wr = new BufferedWriter(new FileWriter(filename, false));

		for (int i=0 ; i<featureVectorsAndGoldGroups.size(); i++)
			for (Pair<T, Double> vectAndGold: featureVectorsAndGoldGroups.get(i))
				writeLineLibSvm(fn.ftrToNormalizedFtrArray(vectAndGold.first), wr, vectAndGold.second, i);;
		wr.close();
	}

	private void writeLineLibSvm(double[] ftrVect, BufferedWriter wr, double gold, int id)
			throws IOException {
		String line = String.format("%.5f ", gold);
		for (int ftr = 0; ftr < ftrVect.length; ftr++)
			line += String.format("%d:%.9f ", ftr + 1, ftrVect[ftr]);
		line += " #id="+id;
		wr.write(line + "\n");
	}

	private void writeLineRankLib(double[] ftrVect, BufferedWriter wr, int rank, int groupid)
			throws IOException {
		String line = String.format("%d qid:%d ", rank, groupid);
		for (int ftr = 0; ftr < ftrVect.length; ftr++)
			line += String.format("%d:%.9f ", ftr + 1, ftrVect[ftr]);
		wr.write(line + "\n");
	}
	
	public void dumpExamplesRankLib(String filename, FeatureNormalizer fn) throws IOException {
		BufferedWriter wr = new BufferedWriter(new FileWriter(filename, false));

		for (int groupId=0; groupId< featureVectorsAndGoldGroups.size(); groupId++){
			List<Pair<T, Double>> featureVectorsAndGolds = new Vector<>(featureVectorsAndGoldGroups.get(groupId));
			Collections.sort(featureVectorsAndGolds, new SmaphUtils.ComparePairsBySecondElement());
			Collections.reverse(featureVectorsAndGolds);
			int rank = 0;
			double lastVal = Double.NaN;
			for (Pair<T, Double> pair : featureVectorsAndGolds){
				if (pair.second != lastVal){
					lastVal = pair.second;
					rank++;
				}
				writeLineRankLib(fn.ftrToNormalizedFtrArray(pair.first), wr, rank, groupId);
			}
				
		}
		wr.close();
	}
}
