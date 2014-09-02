package it.acubelab.smaph.linkback;

import static org.junit.Assert.*;
import it.acubelab.batframework.data.ScoredAnnotation;
import it.acubelab.batframework.data.Tag;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import org.junit.Test;

public class BaselineLinkBackTest {

	@Test
	public void testLinkBack() {
		BaselineLinkBack lb = new BaselineLinkBack(null);
		{
			String query = "armstrong mon   lading";
			HashMap<String[], Tag> boldsToEntities = new HashMap<>();
			boldsToEntities.put(new String[] { "moon landing", "wikipedia",
					"moon" }, new Tag(111));
			boldsToEntities.put(new String[] { "armstrong", "neil armstrong" },
					new Tag(222));
			boldsToEntities.put(new String[] { "armstrang", "neil armstrang" },
					new Tag(333));

			HashSet<ScoredAnnotation> res = lb.linkBack(query, boldsToEntities);
			Vector<ScoredAnnotation> resVect = new Vector<>(res);
			Collections.sort(resVect);
			assertEquals(2, res.size());
			
			assertEquals(222, resVect.get(0).getConcept());
			assertEquals(0, resVect.get(0).getPosition());
			assertEquals(9, resVect.get(0).getLength());
			
			assertEquals(111, resVect.get(1).getConcept());
			assertEquals(10, resVect.get(1).getPosition());
			assertEquals(12, resVect.get(1).getLength());
		}
		
		{
			String query = "armstrang trumpet";
			HashMap<String[], Tag> boldsToEntities = new HashMap<>();
			boldsToEntities.put(new String[] { "moon landing", "wikipedia",
					"moon" }, new Tag(111));
			boldsToEntities.put(new String[] { "armstrong", "neil armstrong" },
					new Tag(222));

			HashSet<ScoredAnnotation> res = lb.linkBack(query, boldsToEntities);
			Vector<ScoredAnnotation> resVect = new Vector<>(res);
			Collections.sort(resVect);
			assertEquals(1, res.size());
			
			assertEquals(222, resVect.get(0).getConcept());
			assertEquals(0, resVect.get(0).getPosition());
			assertEquals(9, resVect.get(0).getLength());
		}
	}
}
