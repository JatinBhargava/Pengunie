package com.pengunie.search;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkerTest {

	private final TokenCounter tokens = new TokenCounter();

	private final Chunker chunker = new Chunker(tokens, new ChunkingProperties(100, 20));

	@Test
	void smallDocumentIsOneChunkWithPageRange() {
		var chunks = chunker.chunk(List.of("Alpha paragraph.", "Beta paragraph."));

		assertThat(chunks).singleElement().satisfies(c -> {
			assertThat(c.pageStart()).isEqualTo(1);
			assertThat(c.pageEnd()).isEqualTo(2);
			assertThat(c.content()).contains("Alpha paragraph.").contains("Beta paragraph.");
		});
	}

	@Test
	void chunksRespectTargetSizeAndKeepPageProvenance() {
		List<String> pages = IntStream.rangeClosed(1, 5)
			.mapToObj(p -> IntStream.range(0, 6)
				.mapToObj(i -> "Page " + p + " sentence " + i + " talks about distributed systems and consensus.")
				.collect(Collectors.joining("\n\n")))
			.toList();

		var chunks = chunker.chunk(pages);

		assertThat(chunks).hasSizeGreaterThan(3);
		assertThat(chunks).allSatisfy(c -> {
			assertThat(c.tokenCount()).isLessThanOrEqualTo(100);
			assertThat(c.pageStart()).isLessThanOrEqualTo(c.pageEnd());
			assertThat(c.content()).contains("Page " + c.pageStart() + " ");
			assertThat(c.content()).contains("Page " + c.pageEnd() + " ");
		});
		assertThat(chunks.getFirst().pageStart()).isEqualTo(1);
		assertThat(chunks.getLast().pageEnd()).isEqualTo(5);
		assertThat(chunks).extracting(Chunker.Chunk::ordinal).containsExactlyElementsOf(
				IntStream.range(0, chunks.size()).boxed().toList());
	}

	@Test
	void consecutiveChunksOverlap() {
		String page = IntStream.range(0, 30)
			.mapToObj(i -> "Unique sentence number " + i + " about retrieval.")
			.collect(Collectors.joining("\n\n"));

		var chunks = chunker.chunk(List.of(page));

		assertThat(chunks).hasSizeGreaterThan(1);
		for (int i = 1; i < chunks.size(); i++) {
			String previousLastParagraph = chunks.get(i - 1).content().substring(
					chunks.get(i - 1).content().lastIndexOf("\n\n") + 2);
			assertThat(chunks.get(i).content()).startsWith(previousLastParagraph.substring(0, 20));
		}
	}

	@Test
	void oversizedParagraphIsSplitBySentenceThenTokens() {
		String longSentence = "word ".repeat(400).strip() + ".";
		var chunks = chunker.chunk(List.of(longSentence));

		assertThat(chunks).hasSizeGreaterThanOrEqualTo(4);
		assertThat(chunks).allSatisfy(c -> assertThat(c.tokenCount()).isLessThanOrEqualTo(100));
	}

	@Test
	void tracksNearestHeading() {
		var chunks = new Chunker(tokens, new ChunkingProperties(20, 0)).chunk(List.of(
				"# Insurance\n\nThe policy number is ABC-123 and it renews every year in March.\n\n"
						+ "## Passport\n\nThe passport expires on 15 March 2031 and must be renewed early."));

		assertThat(chunks).extracting(Chunker.Chunk::heading).contains("Insurance", "Passport");
	}

	@Test
	void headingDetection() {
		assertThat(Chunker.headingOf("## Results")).isEqualTo("Results");
		assertThat(Chunker.headingOf("2.1 System Design")).isEqualTo("2.1 System Design");
		assertThat(Chunker.headingOf("EXECUTIVE SUMMARY")).isEqualTo("EXECUTIVE SUMMARY");
		assertThat(Chunker.headingOf("This is a normal sentence.")).isNull();
	}

}
