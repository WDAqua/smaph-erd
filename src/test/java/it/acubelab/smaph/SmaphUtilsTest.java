package it.acubelab.smaph;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import it.acubelab.batframework.utils.Pair;
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
			assertEquals(0, SmaphUtils.getMinEditDist("armstrong moon",
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
			assertEquals(1.0 / 4.0,
					SmaphUtils.getMinEditDist("moooon moan", "moon"), DELTA);

			List<String> minTokens = new Vector<>();
			double res = SmaphUtils.getMinEditDist("moooon moan", "moon",
					minTokens);
			assertEquals(1.0 / 4.0, res, DELTA);
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
	public void testGetBioSequences() {
		{
			List<String> seq1 = SmaphUtils.getBioSequences(1, 100);
			assertEquals(seq1.size(), 2);
			assertEquals(true, seq1.contains("B"));
			assertEquals(true, seq1.contains("O"));
		}
		{
			List<String> seq2 = SmaphUtils.getBioSequences(2, 100);
			assertEquals(seq2.size(), 5);
			assertEquals(true, seq2.contains("BB"));
			assertEquals(true, seq2.contains("BI"));
			assertEquals(true, seq2.contains("BO"));
			assertEquals(true, seq2.contains("OB"));
			assertEquals(true, seq2.contains("OO"));
		}
		{
			List<String> seq3 = SmaphUtils.getBioSequences(3, 100);
			assertEquals(seq3.size(), 13);
			assertEquals(true, seq3.contains("BBB"));
			assertEquals(true, seq3.contains("BBI"));
			assertEquals(true, seq3.contains("BBO"));
			assertEquals(true, seq3.contains("BIB"));
			assertEquals(true, seq3.contains("BII"));
			assertEquals(true, seq3.contains("BIO"));
			assertEquals(true, seq3.contains("BOB"));
			assertEquals(true, seq3.contains("BOO"));
			assertEquals(true, seq3.contains("OBB"));
			assertEquals(true, seq3.contains("OBI"));
			assertEquals(true, seq3.contains("OBO"));
			assertEquals(true, seq3.contains("OOB"));
			assertEquals(true, seq3.contains("OOO"));
		}

	}

	@Test
	public void testFindTokensPosition() throws Exception {
		{
			List<Pair<Integer, Integer>> res = SmaphUtils
					.findTokensPosition("all your base are belong to us.");
			assertEquals(7, res.size());
			assertEquals(res.get(0).first.intValue(), 0);
			assertEquals(res.get(0).second.intValue(), 3);
			assertEquals(res.get(1).first.intValue(), 4);
			assertEquals(res.get(1).second.intValue(), 8);
			assertEquals(res.get(2).first.intValue(), 9);
			assertEquals(res.get(2).second.intValue(), 13);
			assertEquals(res.get(3).first.intValue(), 14);
			assertEquals(res.get(3).second.intValue(), 17);
			assertEquals(res.get(4).first.intValue(), 18);
			assertEquals(res.get(4).second.intValue(), 24);
			assertEquals(res.get(5).first.intValue(), 25);
			assertEquals(res.get(5).second.intValue(), 27);
			assertEquals(res.get(6).first.intValue(), 28);
			assertEquals(res.get(6).second.intValue(), 30);
		}
		{
			List<Pair<Integer, Integer>> res = SmaphUtils
					.findTokensPosition("  lulz   hahhh");
			assertEquals(2, res.size());
			assertEquals(res.get(0).first.intValue(), 2);
			assertEquals(res.get(0).second.intValue(), 6);
			assertEquals(res.get(1).first.intValue(), 9);
			assertEquals(res.get(1).second.intValue(), 14);
		}
		{
			List<Pair<Integer, Integer>> res = SmaphUtils
					.findTokensPosition("  lulz   hahhh  !! ");
			assertEquals(2, res.size());
			assertEquals(res.get(0).first.intValue(), 2);
			assertEquals(res.get(0).second.intValue(), 6);
			assertEquals(res.get(1).first.intValue(), 9);
			assertEquals(res.get(1).second.intValue(), 14);

		}
	}

	@Test
	public void testGetSegmentations() throws Exception {
		String query = "  all , 0your   base!!  ";
		List<List<Pair<Integer, Integer>>> segmentations = SmaphUtils
				.getSegmentations(query, 1000);
		assertEquals(13, segmentations.size());
		HashSet<Integer> verified = new HashSet<>();

		for (List<Pair<Integer, Integer>> segmentationIdx : segmentations) {
			List<String> segmentationStr = new Vector<>();
			for (Pair<Integer, Integer> p : segmentationIdx)
				segmentationStr.add(query.substring(p.first, p.second));

			if (segmentationStr.size() == 3
					&& segmentationStr.get(0).equals("all")
					&& segmentationStr.get(1).equals("0your")
					&& segmentationStr.get(2).equals("base"))
				verified.add(0);
			else if (segmentationStr.size() == 2
					&& segmentationStr.get(0).equals("all")
					&& segmentationStr.get(1).equals("0your   base"))
				verified.add(1);
			else if (segmentationStr.size() == 2
					&& segmentationStr.get(0).equals("all")
					&& segmentationStr.get(1).equals("0your"))
				verified.add(2);
			else if (segmentationStr.size() == 2
					&& segmentationStr.get(0).equals("all , 0your")
					&& segmentationStr.get(1).equals("base"))
				verified.add(3);
			else if (segmentationStr.size() == 1
					&& segmentationStr.get(0).equals("all , 0your   base"))
				verified.add(4);
			else if (segmentationStr.size() == 1
					&& segmentationStr.get(0).equals("all , 0your"))
				verified.add(5);
			else if (segmentationStr.size() == 2
					&& segmentationStr.get(0).equals("all")
					&& segmentationStr.get(1).equals("base"))
				verified.add(6);
			else if (segmentationStr.size() == 1
					&& segmentationStr.get(0).equals("all"))
				verified.add(7);
			else if (segmentationStr.size() == 2
					&& segmentationStr.get(0).equals("0your")
					&& segmentationStr.get(1).equals("base"))
				verified.add(8);
			else if (segmentationStr.size() == 1
					&& segmentationStr.get(0).equals("0your   base"))
				verified.add(9);
			else if (segmentationStr.size() == 1
					&& segmentationStr.get(0).equals("0your"))
				verified.add(10);
			else if (segmentationStr.size() == 1
					&& segmentationStr.get(0).equals("base"))
				verified.add(11);
			else if (segmentationStr.size() == 0)
				verified.add(12);

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
		assertEquals(true, verified.contains(12));
	}

	@Test
	public void testFindSegments() throws Exception {
		List<Pair<Integer, Integer>> segments = SmaphUtils.findSegments("  aaa bbb   ccc   ");
		assertEquals(6, segments.size());
		
		assertEquals(2, segments.get(0).first.intValue());
		assertEquals(5, segments.get(0).second.intValue());
		assertEquals(6, segments.get(1).first.intValue());
		assertEquals(9, segments.get(1).second.intValue());
		assertEquals(12, segments.get(2).first.intValue());
		assertEquals(15, segments.get(2).second.intValue());
		assertEquals(2, segments.get(3).first.intValue());
		assertEquals(9, segments.get(3).second.intValue());
		assertEquals(6, segments.get(4).first.intValue());
		assertEquals(15, segments.get(4).second.intValue());
		assertEquals(2, segments.get(5).first.intValue());
		assertEquals(15, segments.get(5).second.intValue());
	}

	@Test
	public void testGetNonAlphanumericCharCount() throws Exception {
		assertEquals(0, SmaphUtils.getNonAlphanumericCharCount(" dd    34"));
		assertEquals(1, SmaphUtils.getNonAlphanumericCharCount(" dd;34"));
		assertEquals(8, SmaphUtils.getNonAlphanumericCharCount(" dd;34.)*&*+^"));
	}

	@Test
	public void testBoldPairsToListLC() throws Exception {
		List<Pair<String,Integer>> boldAndRanks = new Vector<>();
		boldAndRanks.add(new Pair<String, Integer>("aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("aaa", 1));
		boldAndRanks.add(new Pair<String, Integer>("aaa", 6));
		boldAndRanks.add(new Pair<String, Integer>("bbb", 5));
		boldAndRanks.add(new Pair<String, Integer>("CCC", 5));
		boldAndRanks.add(new Pair<String, Integer>("ccc", 6));
		List<String> bolds = SmaphUtils.boldPairsToListLC(boldAndRanks);
		assertEquals(bolds.size(), 7);
		assertEquals(bolds.get(0), "aaa");
		assertEquals(bolds.get(1), "aaa");
		assertEquals(bolds.get(2), "aaa");
		assertEquals(bolds.get(3), "aaa");
		assertEquals(bolds.get(4), "bbb");
		assertEquals(bolds.get(5), "ccc");
		assertEquals(bolds.get(6), "ccc");
	}

	@Test
	public void testGetFragmentation() throws Exception {
		List<Pair<String, Integer>> boldAndRanks = new Vector<>();
		boldAndRanks.add(new Pair<String, Integer>("aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("aaa bbb", 1));
		boldAndRanks.add(new Pair<String, Integer>("aaa BBB", 1));
		boldAndRanks.add(new Pair<String, Integer>("aaa bbb", 4));
		boldAndRanks.add(new Pair<String, Integer>("aaa", 6));
		boldAndRanks.add(new Pair<String, Integer>("bbb aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("CCC", 5));
		boldAndRanks.add(new Pair<String, Integer>("ccc", 6));
		boldAndRanks.add(new Pair<String, Integer>("bbb", 6));
		List<String> bolds = SmaphUtils.boldPairsToListLC(boldAndRanks);
		assertEquals(1.0, SmaphUtils.getFragmentation(bolds, "aaa"), DELTA);
		assertEquals(3.0 / 7.0,
				SmaphUtils.getFragmentation(bolds, "aaa ' bbb  "), DELTA);
		assertEquals(1.0 / 5.0, SmaphUtils.getFragmentation(bolds, "BBB aaa"),
				DELTA);
		assertEquals(1.0, SmaphUtils.getFragmentation(bolds, "ccc"), DELTA);
		assertEquals(1.0, SmaphUtils.getFragmentation(bolds, "bbb"), DELTA);
	}

	@Test
	public void testGetAggregation() throws Exception {
		List<Pair<String, Integer>> boldAndRanks = new Vector<>();
		boldAndRanks.add(new Pair<String, Integer>("aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("aaa bbb", 1));
		boldAndRanks.add(new Pair<String, Integer>("aaa BBB", 1));
		boldAndRanks.add(new Pair<String, Integer>("aaa bbb", 4));
		boldAndRanks.add(new Pair<String, Integer>("aaa bbb ccc", 9));
		boldAndRanks.add(new Pair<String, Integer>("aaa", 6));
		boldAndRanks.add(new Pair<String, Integer>("bbb aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("CCC", 5));
		boldAndRanks.add(new Pair<String, Integer>("ccc", 6));
		List<String> bolds = SmaphUtils.boldPairsToListLC(boldAndRanks);
		assertEquals(3.0 / 8.0, SmaphUtils.getAggregation(bolds, "aaa"),
				DELTA);
		assertEquals(3.0 / 4.0,
				SmaphUtils.getAggregation(bolds, "aaa ' bbb  "), DELTA);
		assertEquals(1.0 , SmaphUtils.getAggregation(bolds, "BBB aaa"),
				DELTA);
		assertEquals(2.0 / 3.0, SmaphUtils.getAggregation(bolds, "ccc"),
				DELTA);
		assertEquals(0.0, SmaphUtils.getAggregation(bolds, "bbb"), DELTA);
		assertEquals(1.0,
				SmaphUtils.getAggregation(bolds, "aaa   &^*# bbb CCC"), DELTA);
	}

	@Test
	public void testIsSubToken() throws Exception {
		List<String> tokens1 = new Vector<>();
		tokens1.add("aaa");
		tokens1.add("bbb");
		tokens1.add("ccc");
		tokens1.add("ddd");
		tokens1.add("eee");
		
		List<String> tokens2 = new Vector<>();
		tokens2.add("bbb");
		tokens2.add("ccc");
		tokens2.add("ddd");
		
		List<String> tokens3 = new Vector<>();
		tokens3.add("ccc");
		tokens3.add("eee");
		
		List<String> tokens4 = new Vector<>();
		tokens4.add("ccc");
		tokens4.add("ddd");
		tokens4.add("eee");

		List<String> tokens5 = new Vector<>();
		tokens5.add("aaa");
		
		assertEquals(false, SmaphUtils.isSubToken(tokens1, tokens1));
		assertEquals(false, SmaphUtils.isSubToken(tokens2, tokens2));
		assertEquals(false, SmaphUtils.isSubToken(tokens3, tokens3));
		assertEquals(false, SmaphUtils.isSubToken(tokens4, tokens4));
		assertEquals(false, SmaphUtils.isSubToken(tokens5, tokens5));
		
		assertEquals(true, SmaphUtils.isSubToken(tokens2, tokens1));
		assertEquals(false, SmaphUtils.isSubToken(tokens1, tokens2));
		
		assertEquals(false, SmaphUtils.isSubToken(tokens3, tokens1));
		assertEquals(false, SmaphUtils.isSubToken(tokens1, tokens3));
		
		assertEquals(true, SmaphUtils.isSubToken(tokens4, tokens1));
		assertEquals(false, SmaphUtils.isSubToken(tokens1, tokens4));

		assertEquals(true, SmaphUtils.isSubToken(tokens5, tokens1));
		assertEquals(false, SmaphUtils.isSubToken(tokens1, tokens5));
		
		assertEquals(false, SmaphUtils.isSubToken(tokens3, tokens2));
		assertEquals(false, SmaphUtils.isSubToken(tokens2, tokens3));
		
	}

}
