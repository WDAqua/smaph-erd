package it.acubelab.erd.emptyqueryfilters;

import java.util.HashMap;

public interface EmptyQueryFilter {
	public boolean filterQuery(HashMap<String, Double> queryFtrs);
}
