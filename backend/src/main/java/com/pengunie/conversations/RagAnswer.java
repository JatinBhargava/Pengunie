package com.pengunie.conversations;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Structured output contract the model must return for a RAG answer. */
public record RagAnswer(
		@JsonPropertyDescription("false when the sources do not contain the answer") boolean answerable,
		@JsonPropertyDescription("the answer, citing sources inline as [n]") String answer,
		@JsonPropertyDescription("one entry per cited claim") List<Citation> citations) {

	public record Citation(@JsonPropertyDescription("the id attribute of the cited <source>") int sourceId,
			@JsonPropertyDescription("a short exact quote from that source supporting the claim") String quote) {
	}

}
