package it.acubelab.erd;

import java.util.HashSet;
import java.util.List;

import it.acubelab.batframework.metrics.MatchRelation;
import it.acubelab.batframework.utils.Pair;

public class IndexMatch implements MatchRelation<Integer> {

	@Override
	public boolean match(Integer index1, Integer index2) {
		return index1.intValue() == index2.intValue();
	}

	@Override
	public List<HashSet<Integer>> preProcessOutput(
			List<HashSet<Integer>> computedOutput) {
		return computedOutput;
	}

	@Override
	public List<HashSet<Integer>> preProcessGoldStandard(
			List<HashSet<Integer>> goldStandard) {
		return goldStandard;
	}

	@Override
	public String getName() {
		return "Index match Relation";
	}

}
