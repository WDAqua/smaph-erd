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

/**
 * A gatherer of examples for a binary classifier. An example is a pair
 * &lt;features, expected result&gt; generated from an instance, where
 * expected_result is either 'positive' or 'negative'. An instance may generate
 * zero or more pairs. Pairs generated from the same instance form a group.
 */
public class BinaryExampleGatherer extends ExampleGatherer {
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
		List<Pair<double[], Double>> ftrVectorsAndGold = new Vector<>();
		for (double[] posVect : posVectors)
			ftrVectorsAndGold.add(new Pair<double[], Double>(posVect, 1.0));
		for (double[] negVect : negVectors)
			ftrVectorsAndGold.add(new Pair<double[], Double>(negVect, -1.0));
		addExample(ftrVectorsAndGold);
	}
}
