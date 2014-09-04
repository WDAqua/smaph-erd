package it.acubelab.smaph.linkback;

import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.data.Tag;

import java.util.*;

import static org.junit.Assert.*;

import org.junit.Test;

public class SvmLinkBackTest {

	@Test
	public void testGetAllBindings() throws Exception {

		SvmLinkBack lb = new SvmLinkBack(null, 0.3);

		String query = "metronome satting of allegro";
		HashMap<Tag, String[]> entityToTexts = new HashMap<>();

		Tag tag1 = new Tag(88771); // Metronome
		String[] text1 = new String[] { "metronome", "metronomes", "Set",
				"Metronome", "allegro" };
		entityToTexts.put(tag1, text1);

		Tag tag2 = new Tag(30967); // Tempo
		String[] text2 = new String[] { "allegro", "Metronome", "metronome",
				"of allegro" };
		entityToTexts.put(tag2, text2);

		Tag tag3 = new Tag(3564116); // Setting (narrativity)
		String[] text3 = new String[] { "set", "setting" };
		entityToTexts.put(tag3, text3);

		List<HashSet<Annotation>> possibleBindings = lb.getAllBindings(query,
				entityToTexts);

		for (HashSet<Annotation> binding : possibleBindings) {
			for (Annotation ann : binding)
				System.out.printf(
						"[%s -> %d ] ",
						query.substring(ann.getPosition(), ann.getPosition()
								+ ann.getLength()), ann.getConcept());
			System.out.println();
		}
		assertEquals(24, possibleBindings.size());
		HashSet<Integer> verified = new HashSet<>();

		for (HashSet<Annotation> binding : possibleBindings) {
			if (binding.isEmpty())
				verified.add(0);
			if (binding.size() == 1
					&& binding.contains(new Annotation(0, 9, 88771)))
				verified.add(1);
			if (binding.size() == 1
					&& binding.contains(new Annotation(0, 9, 30967)))
				verified.add(2);
			if (binding.size() == 1
					&& binding.contains(new Annotation(0, 9, 88771)))
				verified.add(3);
			if (binding.size() == 1
					&& binding.contains(new Annotation(21, 7, 88771)))
				verified.add(4);
			if (binding.size() == 1
					&& binding.contains(new Annotation(21, 7, 30967)))
				verified.add(5);
			if (binding.size() == 2
					&& binding.contains(new Annotation(21, 7, 30967))
					&& binding.contains(new Annotation(10, 7, 3564116)))
				verified.add(6);
			if (binding.size() == 2
					&& binding.contains(new Annotation(21, 7, 88771))
					&& binding.contains(new Annotation(10, 7, 3564116)))
				verified.add(7);
			if (binding.size() == 3
					&& binding.contains(new Annotation(0, 9, 88771))
					&& binding.contains(new Annotation(21, 7, 88771))
					&& binding.contains(new Annotation(10, 7, 3564116)))
				verified.add(8);
			if (binding.size() == 3
					&& binding.contains(new Annotation(0, 9, 30967))
					&& binding.contains(new Annotation(21, 7, 88771))
					&& binding.contains(new Annotation(10, 7, 3564116)))
				verified.add(9);
			if (binding.size() == 3
					&& binding.contains(new Annotation(0, 9, 88771))
					&& binding.contains(new Annotation(21, 7, 30967))
					&& binding.contains(new Annotation(10, 7, 3564116)))
				verified.add(10);
			if (binding.size() == 3
					&& binding.contains(new Annotation(0, 9, 30967))
					&& binding.contains(new Annotation(21, 7, 30967))
					&& binding.contains(new Annotation(10, 7, 3564116)))
				verified.add(11);
		}

		assertEquals(true, verified.contains(0));
		assertEquals(true, verified.contains(1));
		assertEquals(true, verified.contains(2));
		assertEquals(true, verified.contains(3));
		assertEquals(true, verified.contains(4));
		assertEquals(true, verified.contains(5));
		assertEquals(true, verified.contains(6));
		assertEquals(true, verified.contains(7));
		assertEquals(true, verified.contains(8));
		assertEquals(true, verified.contains(9));
		assertEquals(true, verified.contains(10));
		assertEquals(true, verified.contains(11));

	}

}
