package it.acubelab.smaph.linkback;

import it.acubelab.batframework.data.*;

import java.util.*;

public class DummyLinkBack implements LinkBack {
	@Override
	public HashSet<ScoredAnnotation> linkBack(String query,
			HashSet<Tag> acceptedEntities,
			HashMap<String, Tag> spotToAnnotation,
			HashMap<Tag, List<HashMap<String, Double>>> entityToFtrVects) {
		HashSet<ScoredAnnotation> res = new HashSet<>();
		for (Tag entity : acceptedEntities)
			res.add(new ScoredAnnotation(0, 1, entity.getConcept(), 1));
		return res;
	}
}
