package it.acubelab.smaph.linkback;

import it.acubelab.batframework.data.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public interface LinkBack {
	public HashSet<ScoredAnnotation> linkBack(String query,
			HashSet<Tag> acceptedEntities, HashMap<String, Tag> spotToAnnotation, HashMap<Tag,List<HashMap<String,Double>>> entityToFtrVects);
}
