package com.pengunie.conversations;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.pengunie.search.ChunkSearchRepository.ChunkHit;

import org.springframework.stereotype.Component;

/**
 * Deterministic post-generation grounding check (PRD §15.1 "citation validation"). A citation is
 * kept only if it points at a source that was actually retrieved and its quote really occurs in
 * that source: verbatim after normalisation, or as a near match (≥ {@value #FUZZY_THRESHOLD} of the
 * quote's words inside one window of the source).
 */
@Component
public class CitationValidator {

	static final double FUZZY_THRESHOLD = 0.85;

	public record ValidCitation(int sourceId, ChunkHit source, String quote) {
	}

	public record Result(Grounding grounding, List<ValidCitation> citations, int dropped) {
	}

	public Result validate(RagAnswer answer, Map<Integer, ChunkHit> sources) {
		if (answer == null || !answer.answerable()) {
			return new Result(Grounding.NOT_FOUND, List.of(), 0);
		}
		List<RagAnswer.Citation> claimed = answer.citations() == null ? List.of() : answer.citations();
		Set<String> seen = new LinkedHashSet<>();
		List<ValidCitation> valid = new ArrayList<>();
		for (RagAnswer.Citation citation : claimed) {
			ChunkHit source = sources.get(citation.sourceId());
			if (source == null || citation.quote() == null || citation.quote().isBlank()) {
				continue;
			}
			if (supports(source.content(), citation.quote()) && seen.add(citation.sourceId() + "|" + citation.quote())) {
				valid.add(new ValidCitation(citation.sourceId(), source, citation.quote().strip()));
			}
		}
		int dropped = claimed.size() - valid.size();
		return new Result(valid.isEmpty() ? Grounding.UNVERIFIED : Grounding.GROUNDED, valid, dropped);
	}

	static boolean supports(String sourceText, String quote) {
		String source = normalize(sourceText);
		String needle = normalize(quote);
		if (needle.isEmpty()) {
			return false;
		}
		if (source.contains(needle)) {
			return true;
		}
		return fuzzyCoverage(source.split(" "), needle.split(" ")) >= FUZZY_THRESHOLD;
	}

	/** Best fraction of quote words found (as a multiset) in any same-length window of the source. */
	static double fuzzyCoverage(String[] sourceWords, String[] quoteWords) {
		if (quoteWords.length < 4 || sourceWords.length == 0) {
			return 0; // short quotes must match verbatim
		}
		Map<String, Integer> wanted = counts(Arrays.asList(quoteWords));
		int window = Math.min(sourceWords.length, quoteWords.length + 2);
		Map<String, Integer> inWindow = counts(Arrays.asList(sourceWords).subList(0, window));
		int best = overlap(wanted, inWindow);
		for (int start = 1; start + window <= sourceWords.length; start++) {
			inWindow.merge(sourceWords[start - 1], -1, Integer::sum);
			inWindow.merge(sourceWords[start + window - 1], 1, Integer::sum);
			best = Math.max(best, overlap(wanted, inWindow));
		}
		return (double) best / quoteWords.length;
	}

	private static int overlap(Map<String, Integer> wanted, Map<String, Integer> available) {
		int total = 0;
		for (var entry : wanted.entrySet()) {
			total += Math.min(entry.getValue(), Math.max(0, available.getOrDefault(entry.getKey(), 0)));
		}
		return total;
	}

	private static Map<String, Integer> counts(List<String> words) {
		Map<String, Integer> counts = new HashMap<>();
		words.forEach(w -> counts.merge(w, 1, Integer::sum));
		return counts;
	}

	static String normalize(String text) {
		String folded = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		folded = folded.replaceAll("[\\u2018\\u2019\\u201C\\u201D\"'`]", "")
			.replaceAll("[^\\p{L}\\p{N}]+", " ")
			.strip();
		return folded;
	}

}
