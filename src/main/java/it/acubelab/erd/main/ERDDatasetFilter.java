package it.acubelab.erd.main;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.data.Mention;
import it.acubelab.batframework.data.Tag;
import it.acubelab.batframework.problems.A2WDataset;
import it.acubelab.batframework.problems.C2WDataset;
import it.acubelab.batframework.problems.D2WDataset;
import it.acubelab.batframework.utils.FreebaseApi;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.erd.SmaphAnnotatorDebugger;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

public class ERDDatasetFilter implements A2WDataset {
	private List<HashSet<Tag>> ERDTopics;
	private A2WDataset ds;
	private List<HashSet<Mention>> ERDMentions;
	private List<HashSet<Annotation>> ERDAnnotations;

	public ERDDatasetFilter(A2WDataset ds, WikipediaApiInterface wikiApi,
			WikipediaToFreebase wikiToFreebase) throws IOException {
		this.ds = ds;
		FilterERDTopics(ds.getC2WGoldStandardList(), wikiApi, wikiToFreebase);
		FilterERDAnnotations(ds.getA2WGoldStandardList(), wikiApi,
				wikiToFreebase);
	}

	public static boolean EntityIsNE(WikipediaApiInterface wikiApi,
			WikipediaToFreebase wikiToFreebase, int wid) throws IOException {
		String title = wikiApi.getTitlebyId(wid);
		return EntityIsNE(wikiApi, wikiToFreebase, title);
	}
	public static boolean EntityIsNE(WikipediaApiInterface wikiApi,
			WikipediaToFreebase wikiToFreebase, String title) throws IOException {
		return title != null && wikiToFreebase.hasEntity(title);
	}

	private void FilterERDAnnotations(
			List<HashSet<Annotation>> a2wGoldStandardList,
			WikipediaApiInterface wikiApi, WikipediaToFreebase wikiToFreebase)
			throws IOException {
		ERDMentions = new Vector<HashSet<Mention>>();
		ERDAnnotations = new Vector<HashSet<Annotation>>();
		for (HashSet<Annotation> anns : a2wGoldStandardList) {
			HashSet<Annotation> filteredAnns = new HashSet<>();
			ERDAnnotations.add(filteredAnns);
			HashSet<Mention> filteredMentions = new HashSet<>();
			ERDMentions.add(filteredMentions);
			for (Annotation ann : anns) {
				String title = wikiApi.getTitlebyId(ann.getConcept());
				if (!EntityIsNE(wikiApi, wikiToFreebase, ann.getConcept())) {
					SmaphAnnotatorDebugger.out.printf("Discarding title=%s%n", title);
					continue;
				}
				SmaphAnnotatorDebugger.out.printf("Including title=%s%n", title);
				filteredAnns.add(ann);
				filteredMentions.add(new Mention(ann.getPosition(), ann
						.getLength()));
			}

		}

	}

	private void FilterERDTopics(List<HashSet<Tag>> c2wGoldStandardList,
			WikipediaApiInterface wikiApi, WikipediaToFreebase wikiToFreebase)
			throws IOException {
		ERDTopics = new Vector<>();
		for (HashSet<Tag> tags : c2wGoldStandardList) {
			HashSet<Tag> erdTags = new HashSet<>();
			ERDTopics.add(erdTags);
			for (Tag t : tags) {
				String title = wikiApi.getTitlebyId(t.getConcept());
				if (!EntityIsNE(wikiApi, wikiToFreebase, t.getConcept())) {
					SmaphAnnotatorDebugger.out.printf("Discarding title=%s%n", title);
					continue;
				}
				SmaphAnnotatorDebugger.out.printf("Including title=%s%n", title);
				erdTags.add(new Tag(t.getConcept()));
			}

		}
	}

	@Override
	public int getSize() {
		return ds.getSize();
	}

	@Override
	public String getName() {
		return ds.getName() + " (ERD)";
	}

	@Override
	public List<String> getTextInstanceList() {
		return ds.getTextInstanceList();
	}

	@Override
	public int getTagsCount() {
		int count = 0;
		for (HashSet<Annotation> s : ERDAnnotations)
			count += s.size();
		return count;
	}

	@Override
	public List<HashSet<Tag>> getC2WGoldStandardList() {
		return ERDTopics;
	}

	@Override
	public List<HashSet<Mention>> getMentionsInstanceList() {
		return ERDMentions;
	}

	@Override
	public List<HashSet<Annotation>> getD2WGoldStandardList() {
		return getA2WGoldStandardList();
	}

	@Override
	public List<HashSet<Annotation>> getA2WGoldStandardList() {
		return ERDAnnotations;
	}

}
