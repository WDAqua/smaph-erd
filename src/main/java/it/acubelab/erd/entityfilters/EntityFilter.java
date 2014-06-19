package it.acubelab.erd.entityfilters;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.data.Mention;
import it.acubelab.batframework.utils.Pair;

public interface EntityFilter {
	public boolean filterEntity(HashMap<String, Double> features);
}
