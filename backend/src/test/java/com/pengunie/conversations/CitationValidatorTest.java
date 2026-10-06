package com.pengunie.conversations;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pengunie.search.ChunkSearchRepository.ChunkHit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CitationValidatorTest {

	private final CitationValidator validator = new CitationValidator();

	private final ChunkHit source = new ChunkHit(UUID.randomUUID(), UUID.randomUUID(), "Policy", 3, 3, null,
			"The health insurance policy HX-221 renews on 10 December 2026. The annual premium is ₹18,400, payable "
					+ "by bank transfer.",
			"", 0.8);

	private final Map<Integer, ChunkHit> sources = Map.of(1, source);

	@Test
	void verbatimQuoteIsValid() {
		var result = validator.validate(answer(new RagAnswer.Citation(1, "renews on 10 December 2026")), sources);

		assertThat(result.grounding()).isEqualTo(Grounding.GROUNDED);
		assertThat(result.citations()).singleElement().satisfies(c -> assertThat(c.source()).isEqualTo(source));
	}

	@Test
	void quoteWithDifferentPunctuationAndCaseIsValid() {
		var result = validator.validate(answer(new RagAnswer.Citation(1, "\u201CThe annual premium is ₹18,400\u201D")),
				sources);

		assertThat(result.grounding()).isEqualTo(Grounding.GROUNDED);
	}

	@Test
	void nearQuoteAboveThresholdIsValid() {
		var result = validator.validate(
				answer(new RagAnswer.Citation(1, "the annual premium is ₹18,400 payable via bank transfer")), sources);

		assertThat(result.grounding()).isEqualTo(Grounding.GROUNDED);
	}

	@Test
	void fabricatedQuoteIsDropped() {
		var result = validator.validate(answer(new RagAnswer.Citation(1, "the policy covers dental and vision care")),
				sources);

		assertThat(result.grounding()).isEqualTo(Grounding.UNVERIFIED);
		assertThat(result.citations()).isEmpty();
		assertThat(result.dropped()).isEqualTo(1);
	}

	@Test
	void citationToUnretrievedSourceIsDropped() {
		var result = validator.validate(answer(new RagAnswer.Citation(7, "renews on 10 December 2026")), sources);

		assertThat(result.grounding()).isEqualTo(Grounding.UNVERIFIED);
	}

	@Test
	void mixedCitationsKeepOnlyValidOnes() {
		var result = validator.validate(answer(new RagAnswer.Citation(1, "renews on 10 December 2026"),
				new RagAnswer.Citation(2, "invented")), sources);

		assertThat(result.grounding()).isEqualTo(Grounding.GROUNDED);
		assertThat(result.citations()).hasSize(1);
		assertThat(result.dropped()).isEqualTo(1);
	}

	@Test
	void unanswerableIsNotFound() {
		var result = validator.validate(new RagAnswer(false, "Not covered.", List.of()), sources);

		assertThat(result.grounding()).isEqualTo(Grounding.NOT_FOUND);
	}

	private static RagAnswer answer(RagAnswer.Citation... citations) {
		return new RagAnswer(true, "Answer [1]", List.of(citations));
	}

}
