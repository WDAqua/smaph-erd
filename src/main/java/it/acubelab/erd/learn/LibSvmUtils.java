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

import it.acubelab.batframework.utils.Pair;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Vector;

import libsvm.svm_node;
import libsvm.svm_problem;

public class LibSvmUtils {
	public static double scale(double value, double rangeMin, double rangeMax) {
		return rangeMax == rangeMin? 0.0 : (value - rangeMin) / (rangeMax - rangeMin) * 2f - 1;
	}
	
	public static svm_node[] featuresArrayToNode(double[] ftrArray) {
		Vector<Integer> pickedFtrsI = new Vector<>();
		for (int ftrId=1; ftrId<ftrArray.length+1; ftrId++)
			pickedFtrsI.add(ftrId);
		return featuresArrayToNode(ftrArray, pickedFtrsI);
	}
		
	public static svm_node[] featuresArrayToNode(double[] ftrArray, Vector<Integer> pickedFtrsI) {
		svm_node[] ftrVect = new svm_node[pickedFtrsI.size()];
		for (int i=0; i<pickedFtrsI.size(); i++) {
			int ftrId = pickedFtrsI.get(i);
			ftrVect[i] = new svm_node();
			ftrVect[i].index = ftrId;
			ftrVect[i].value = ftrArray[ftrId-1];
		}
		return ftrVect;
	}

	public static Pair<double[], double[]> findRanges(svm_problem problem) {
		int nftrs = problem.x[0].length;
		double[] rangeMins = new double[nftrs];
		double[] rangeMaxs = new double[nftrs];
		for (int i = 0; i < nftrs; i++) {
			rangeMins[i] = problem.x[0][i].value;
			rangeMaxs[i] = problem.x[0][i].value;
			for (int j = 0; j < problem.x.length; j++) {
				rangeMins[i] = Math.min(rangeMins[i], problem.x[j][i].value);
				rangeMaxs[i] = Math.max(rangeMaxs[i], problem.x[j][i].value);
			}
		}
		return new Pair<>(rangeMins,rangeMaxs);
	}

	public static void scaleProblem(svm_problem problem, double[] rangeMins,
			double[] rangeMaxs) {
		for (int i = 0; i < problem.l; i++)
			scaleNode(problem.x[i], rangeMins, rangeMaxs);
	}

	public static void dumpRanges(double[] mins, double[] maxs, String filename)
			throws IOException {
		BufferedWriter br = new BufferedWriter(new FileWriter(filename));
		br.write("x\n-1 1\n");
		for (int i = 0; i < mins.length; i++)
			br.write(String.format("%d %f %f%n", i + 1, mins[i], maxs[i]));
		br.close();
	}

	public static void scaleNode(svm_node[] ftrVect, double[] rangeMins,
			double[] rangeMaxs) {
		for (int i = 0; i < ftrVect.length; i++)
			ftrVect[i].value = scale(ftrVect[i].value, rangeMins[ftrVect[i].index-1],
					rangeMaxs[ftrVect[i].index-1]);

	}
}
