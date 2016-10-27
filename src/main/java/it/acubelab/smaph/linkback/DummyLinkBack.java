package it.acubelab.smaph.linkback;

import it.unipi.di.acube.batframework.data.*;

import java.util.*;

public class DummyLinkBack implements LinkBack {

	@Override
	public HashSet<ScoredAnnotation> linkBack(String query, HashMap<String[], Tag> boldToEntities) {
		HashSet<ScoredAnnotation> res = new HashSet<>();
		for (Tag entity : boldToEntities.values())
			res.add(new ScoredAnnotation(0, 1, entity.getConcept(), 1));
		return res;
	}

}
