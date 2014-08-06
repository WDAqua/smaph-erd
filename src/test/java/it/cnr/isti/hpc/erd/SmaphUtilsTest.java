package it.cnr.isti.hpc.erd;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Vector;

import it.acubelab.smaph.SmaphUtils;

import org.junit.Test;

public class SmaphUtilsTest {
	private static final double DELTA = 1e-4;

	@Test
	public void testGetMinEditDist() {
		{
			assertEquals((0 + 4.0 / 5.0 + 7.0 / 10.0) / 3.0,
					SmaphUtils.getMinEditDist("armstrong moon",
							"Armstrong World Industries"), DELTA);

			List<String> minTokens = new Vector<>();
			double res = SmaphUtils.getMinEditDist("armstrong moon",
					"Armstrong World Industries", minTokens);
			assertEquals((0 + 4.0 / 5.0 + 7.0 / 10.0) / 3.0, res, DELTA);
			assertEquals(3, minTokens.size());
			assertEquals(true, minTokens.get(0).equals("armstrong"));
			assertEquals(true, minTokens.get(1).equals("moon"));
			assertEquals(true, minTokens.get(2).equals("armstrong"));
		}
		{
			assertEquals(0,
					SmaphUtils.getMinEditDist("armstrong moon",
							"armstrong moon"), 0.0);

			List<String> minTokens = new Vector<>();
			double res = SmaphUtils.getMinEditDist("armstrong moon",
					"armstrong moon", minTokens);
			assertEquals(0, res, 0.0);
			assertEquals(2, minTokens.size());
			assertEquals(true, minTokens.get(0).equals("armstrong"));
			assertEquals(true, minTokens.get(1).equals("moon"));
		}
		{
			assertEquals(1.0/4.0,
					SmaphUtils.getMinEditDist("moooon moan",
							"moon"), DELTA);

			List<String> minTokens = new Vector<>();
			double res = SmaphUtils.getMinEditDist("moooon moan",
					"moon", minTokens);
			assertEquals(1.0/4.0, res, DELTA);
			assertEquals(1, minTokens.size());
			assertEquals(true, minTokens.get(0).equals("moan"));
		}

	}

	@Test
	public void testGetNormEditDistance() {
		assertEquals(0.0,
				SmaphUtils.getNormEditDistance("armstrong", "armstrong"), 0.0);
		assertEquals(8.0 / 9.0,
				SmaphUtils.getNormEditDistance("world", "armstrong"), DELTA);
		assertEquals(4.0 / 5.0,
				SmaphUtils.getNormEditDistance("world", "moon"), DELTA);
		assertEquals(7.0 / 10.0,
				SmaphUtils.getNormEditDistance("industries", "armstrong"),
				DELTA);
		assertEquals(1.0, SmaphUtils.getNormEditDistance("industries", "moon"),
				DELTA);
	}

	@Test
	public void testAcceptWikipediaTitle() {
		fail("Not yet implemented");
	}

	@Test
	public void testGetAllFtrVect() {
		fail("Not yet implemented");
	}

	@Test
	public void testMapRankToBoldsLC() {
		fail("Not yet implemented");
	}

	@Test
	public void testFindPositionsLC() {
		fail("Not yet implemented");
	}

	@Test
	public void testStemString() {
		fail("Not yet implemented");
	}

}
