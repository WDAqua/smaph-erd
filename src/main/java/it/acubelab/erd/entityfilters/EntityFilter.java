package it.acubelab.erd.entityfilters;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.data.Mention;
import it.acubelab.batframework.utils.Pair;

/**
 * An interface to an Entity filter.
 */
public interface EntityFilter {
	/**
	 * @param features
	 *            features of the entity.
	 * @return true iff the entity should be kept.
	 */
	public boolean filterEntity(HashMap<String, Double> features);
}
