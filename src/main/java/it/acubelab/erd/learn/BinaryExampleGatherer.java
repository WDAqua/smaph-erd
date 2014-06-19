package it.acubelab.erd.learn;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import libsvm.svm_node;
import libsvm.svm_problem;

public class BinaryExampleGatherer {
	private Vector<Vector<double[]>> positiveFeatureVectors = new Vector<>();
	private Vector<Vector<double[]>> negativeFeatureVectors = new Vector<>();
	private int ftrCount = -1;

	private static Vector<double[]> getPlain(Vector<Vector<double[]>> vectVect) {
		Vector<double[]> res = new Vector<>();
		for (Vector<double[]> vect : vectVect)
			res.addAll(vect);
		return res;
	}

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

	public svm_problem generateLibSvmProblem() {
		Vector<Integer> allFtrs = new Vector<>();
		for (int i = 1; i < this.getFtrCount() + 1; i++)
			allFtrs.add(i);
		return generateLibSvmProblem(allFtrs);
	}

	public int getFtrCount() {
		return ftrCount;
	}

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

	public svm_problem generateLibSvmProblem(Vector<Integer> pickedFtrsI) {
		Vector<Double> targets = new Vector<Double>();
		Vector<svm_node[]> ftrVectors = new Vector<svm_node[]>();
		for (double[] posVect : getPlain(positiveFeatureVectors)) {
			ftrVectors.add(LibSvmUtils
					.featuresArrayToNode(posVect, pickedFtrsI));
			targets.add(1.0);
		}
		for (double[] negVect : getPlain(negativeFeatureVectors)) {
			ftrVectors.add(LibSvmUtils
					.featuresArrayToNode(negVect, pickedFtrsI));
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

	public Vector<svm_problem> generateLibSvmProblemOnePerInstance(
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

	public int getFtrVectorCount() {
		int count = 0;
		for (Vector<double[]> positiveFeatureVector : positiveFeatureVectors)
			count += positiveFeatureVector.size();
		for (Vector<double[]> negativeFeatureVector : negativeFeatureVectors)
			count += negativeFeatureVector.size();

		return count;
	}
}
