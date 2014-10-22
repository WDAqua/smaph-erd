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

import it.acubelab.smaph.learn.featurePacks.FeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;

import java.io.IOException;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;

/**
 * A wrapper for a LibSvm model.
 * 
 * @author Marco Cornolti
 *
 */
public abstract class LibSvmModel {
	private svm_model model;
	String modelFile;

	public LibSvmModel(String modelFile) throws IOException {
		setModel(modelFile);
	}

	public boolean predict(FeaturePack fp, FeatureNormalizer fn) {
		return predictScore(fp, fn) > 0.0;
	}

	public double predictScore(FeaturePack fp, FeatureNormalizer fn) {
		svm_node[] ftrVect = LibSvmUtils.featuresArrayToNode(fn
				.ftrToNormalizedFtrArray(fp));
		return svm.svm_predict(model, ftrVect);
	}

	public String getModel() {
		return modelFile;
	}

	public void setModel(String modelFile) {
		this.modelFile = modelFile;
		try {
			this.model = svm.svm_load_model(modelFile);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
