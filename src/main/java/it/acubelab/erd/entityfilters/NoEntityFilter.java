package it.acubelab.erd.entityfilters;

import java.util.HashMap;

public class NoEntityFilter implements EntityFilter {

	@Override
	public boolean filterEntity(HashMap<String, Double> features) {
		return true;
	}

}
