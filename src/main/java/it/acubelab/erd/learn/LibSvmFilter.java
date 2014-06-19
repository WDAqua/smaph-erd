package it.acubelab.erd.learn;

import it.acubelab.erd.SmaphAnnotatorDebugger;

import java.io.*;
import java.util.HashMap;
import java.util.Vector;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;

public abstract class LibSvmFilter {
	private svm_model model;
	private double[] rangeMins, rangeMaxs;
	private String modelFile;
	private String rangeFile;

	public LibSvmFilter(String modelFile, String rangeFile) throws IOException {
		setModel(modelFile);
		this.rangeFile = rangeFile;
		resetRanges();
	}

	public abstract double[] featuresToFtrVect(HashMap<String, Double> features);

	
	public boolean predict(HashMap<String, Double> features) {
		svm_node[] ftrVect = LibSvmUtils.featuresArrayToNode(featuresToFtrVect(features));
		LibSvmUtils.scaleNode(ftrVect, rangeMins, rangeMaxs);
		return svm.svm_predict(model, ftrVect) > 0;
	}

	public void resetRanges() {
		Vector<String[]> tokensVect = new Vector<>();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(rangeFile)));
			String line;
			while ((line = reader.readLine()) != null) {
				String[] tokens = line.split(" ");
				if (tokens.length == 3)
					tokensVect.add(tokens);
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		rangeMins = new double[tokensVect.size()];
		rangeMaxs = new double[tokensVect.size()];
		for (String[] tokens : tokensVect) {
			int featureId = Integer.parseInt(tokens[0]);
			float rangeMin = Float.parseFloat(tokens[1]);
			float rangeMax = Float.parseFloat(tokens[2]);
			rangeMins[featureId - 1] = rangeMin;
			rangeMaxs[featureId - 1] = rangeMax;
			SmaphAnnotatorDebugger.out.printf("Feature %d range: [%.3f, %.3f]%n", featureId,
					rangeMin, rangeMax);
		}
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
