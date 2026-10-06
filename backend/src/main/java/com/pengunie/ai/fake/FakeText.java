package com.pengunie.ai.fake;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class FakeText {

	static final Set<String> STOPWORDS = Set.of("the", "and", "for", "are", "was", "were", "what", "which", "who",
			"whom", "this", "that", "these", "those", "with", "from", "into", "about", "does", "did", "has", "have",
			"had", "how", "why", "when", "where", "you", "your", "our", "can", "will", "would", "should", "there",
			"their", "they", "them", "its", "not", "but", "all", "any", "tell", "please", "according", "document",
			"documents", "mentioned", "say", "says");

	private FakeText() {
	}

	static String stem(String word) {
		for (String suffix : new String[] { "ing", "ies", "es", "ed", "s" }) {
			if (word.length() > suffix.length() + 3 && word.endsWith(suffix)) {
				return word.substring(0, word.length() - suffix.length());
			}
		}
		return word;
	}

	static Set<String> terms(String text) {
		return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
			.filter(w -> w.length() >= 3 && !STOPWORDS.contains(w))
			.map(FakeText::stem)
			.collect(Collectors.toSet());
	}

}
