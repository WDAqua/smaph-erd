package it.acubelab.erd.entityfilters;

import java.util.HashMap;

/**
 * An entity filter that does nothing (accepts all entities).
 */
public class NoEntityFilter implements EntityFilter {

	@Override
	public boolean filterEntity(HashMap<String, Double> features) {
		return true;
	}

}
