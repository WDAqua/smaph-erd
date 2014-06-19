package it.acubelab.erd.learn;

import it.acubelab.erd.learn.TuneModel.OptimizaionProfiles;

import java.io.Serializable;
import java.util.List;
import java.util.Vector;

public class ModelConfigurationResult implements Serializable {
	private static final long serialVersionUID = 1L;
	private Vector<Integer> pickedFtrs;
	private double editDistanceThreshold;
	private double wPos;
	private double wNeg;
	private int totalInstances;
	private int tp;
	private int fp;
	private int fn;
	private int tn;
	private float microf1, fnRate, macrof1, macroPrec, macroRec;

	public ModelConfigurationResult(Vector<Integer> pickedFtrsI, double wPos,
			double wNeg, double editDistanceThreshold, int tp, int fp, int fn,
			int tn, float microF1, float macroF1, float macroRec, float macroPrec) {
		this.pickedFtrs = new Vector<>(pickedFtrsI);
		this.wPos = wPos;
		this.wNeg = wNeg;
		this.editDistanceThreshold = editDistanceThreshold;
		this.totalInstances = tp + fp + fn + tn;
		this.tp = tp;
		this.fp = fp;
		this.fn = fn;
		this.tn = tn;
		this.microf1 = microF1;
		this.macrof1 = macroF1;
		this.fnRate = (float) fn / (float) (fn + tp);
		this.macroRec = macroRec;
		this.macroPrec = macroPrec;
	}

	public String getReadable() {
		String ftrsString = "";
		for (int ftrId : pickedFtrs)
			ftrsString += ftrId + ",";
		ftrsString = pickedFtrs.isEmpty() ? "null!" : ftrsString.substring(0,
				ftrsString.length() - 1);
		return String
				.format("Features:%s wPos:%.5f wNeg:%.5f ED-threshold=%.2f tot=%d TP=%d FP=%d FN=%d TN=%d -> FN_rate=%.2f%% mic-F1=%.2f%% mac-P/R/F1=%.2f%%/%.2f%%/%.2f%%",
						ftrsString, wPos, wNeg, editDistanceThreshold,
						totalInstances, tp, fp, fn, tn, fnRate * 100,
						microf1 * 100, macroPrec*100,macroRec*100, macrof1 * 100);
	}

	public Vector<Integer> getFeatures() {
		return this.pickedFtrs;
	}

	public double getWPos() {
		return this.wPos;
	}

	public double getWNeg() {
		return this.wNeg;
	}

	public int getTN() {
		return this.tn;
	}

	public float getMicroF1() {
		return this.microf1;
	}
	
	public float getMacroF1() {
		return this.macrof1;
	}

	public double getFNrate() {
		return this.fnRate;
	}

	public boolean worseThan(ModelConfigurationResult other,
			OptimizaionProfiles optProfile, double optProfileThreshold) {
		boolean betterResult = false;
		if (other == null)
			return true;

		if (optProfile == OptimizaionProfiles.MAXIMIZE_TN)
			betterResult = other.getFNrate() < optProfileThreshold
					&& other.getTN() > this.getTN();
		else if (optProfile == OptimizaionProfiles.MAXIMIZE_MICRO_F1)
			betterResult = other.getMicroF1() > this.getMicroF1();
		else if (optProfile == OptimizaionProfiles.MAXIMIZE_MACRO_F1)
			betterResult = other.getMacroF1() > this.getMacroF1();

		return betterResult;
	}

	public boolean equalResult(ModelConfigurationResult other,
			OptimizaionProfiles optProfile, double optProfileThreshold) {
		boolean equalResult = false;
		if (other == null)
			return true;

		if (optProfile == OptimizaionProfiles.MAXIMIZE_TN)
			equalResult = other.getFNrate() < optProfileThreshold
					&& other.getTN() == this.getTN();
		else if (optProfile == OptimizaionProfiles.MAXIMIZE_MICRO_F1)
			equalResult = other.getMicroF1() == this.getMicroF1();
		else if (optProfile == OptimizaionProfiles.MAXIMIZE_MACRO_F1)
			equalResult = other.getMacroF1() == this.getMacroF1();

		return equalResult;
	}

	public static ModelConfigurationResult findBest(
			List<ModelConfigurationResult> configurations,
			OptimizaionProfiles optProfile, double optProfileThreshold) {
		ModelConfigurationResult best = null;
		for (ModelConfigurationResult conf : configurations)
			if (best == null
					|| best.worseThan(conf, optProfile, optProfileThreshold)
					|| (best.equalResult(conf, optProfile, optProfileThreshold) && best
							.getFeatures().size() > conf.getFeatures().size()))
				best = conf;
		return best;
	}

	public float getMacroPrecision() {
		return this.macroPrec;
	}
	public float getMacroRecall() {
		return this.macroRec;
	}

}
