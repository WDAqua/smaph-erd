package it.cnr.isti.hpc.erd;

import static org.junit.Assert.*;
import it.acubelab.smaph.SmaphUtils;

import org.junit.Test;

public class SmaphUtilsTest {
	private static final double DELTA = 1e-4;

	@Test
	public void testGetMinEditDist() {
		assertEquals((0 + 4.0 / 5.0 + 7.0 / 10.0) / 3.0,
				SmaphUtils.getMinEditDist("armstrong moon",
						"Armstrong World Industries"), DELTA);
	}

	@Test
	public void testGetNormEditDistance() {
		assertEquals(0.0, SmaphUtils.getNormEditDistance("armstrong", "armstrong"),0.0);
		assertEquals(8.0/9.0, SmaphUtils.getNormEditDistance("world", "armstrong"),DELTA);
		assertEquals(4.0/5.0, SmaphUtils.getNormEditDistance("world", "moon"),DELTA);
		assertEquals(7.0/10.0, SmaphUtils.getNormEditDistance("industries", "armstrong"),DELTA);
		assertEquals(1.0, SmaphUtils.getNormEditDistance("industries", "moon"),DELTA);
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
