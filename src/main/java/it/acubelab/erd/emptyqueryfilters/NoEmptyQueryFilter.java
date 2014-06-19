package it.acubelab.erd.emptyqueryfilters;

import java.util.HashMap;

public class NoEmptyQueryFilter implements EmptyQueryFilter {

	@Override
	public boolean filterQuery(HashMap<String, Double> queryFtrs) {
		return true;
	}

}
