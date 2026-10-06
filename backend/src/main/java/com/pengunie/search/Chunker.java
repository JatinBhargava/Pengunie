package com.pengunie.search;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Recursive, token-aware splitter: paragraphs → sentences → hard token cuts, packed greedily into
 * chunks of about {@code targetTokens} with trailing overlap. Every chunk keeps the page range it
 * came from and the nearest preceding heading, which is what makes page-level citations possible.
 */
@Component
public class Chunker {

	public record Chunk(int ordinal, int pageStart, int pageEnd, String heading, String content, int tokenCount) {
	}

	/** Smallest unit that is never split further (a paragraph, sentence or token slice). */
	private record Unit(int page, String heading, String text, int tokens) {
	}

	private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");

	private static final Pattern NUMBERED_HEADING = Pattern.compile("^(\\d+(\\.\\d+)*\\.?|[IVX]+\\.)\\s+\\p{Lu}.{0,80}$");

	private final TokenCounter tokens;

	private final int targetTokens;

	private final int overlapTokens;

	public Chunker(TokenCounter tokens, ChunkingProperties props) {
		this.tokens = tokens;
		this.targetTokens = props.targetTokens();
		this.overlapTokens = props.overlapTokens();
	}

	/** @param pages page texts, index 0 = page 1 */
	public List<Chunk> chunk(List<String> pages) {
		return pack(units(pages));
	}

	private List<Unit> units(List<String> pages) {
		List<Unit> units = new ArrayList<>();
		String heading = null;
		for (int i = 0; i < pages.size(); i++) {
			int page = i + 1;
			for (String paragraph : pages.get(i).split("\\n\\s*\\n")) {
				String text = paragraph.strip();
				if (text.isEmpty()) {
					continue;
				}
				String detected = headingOf(text);
				if (detected != null) {
					heading = detected;
				}
				int count = tokens.count(text);
				if (count <= targetTokens) {
					units.add(new Unit(page, heading, text, count));
					continue;
				}
				for (String sentence : sentences(text)) {
					int sentenceTokens = tokens.count(sentence);
					if (sentenceTokens <= targetTokens) {
						units.add(new Unit(page, heading, sentence, sentenceTokens));
					}
					else {
						for (String piece : tokens.splitByTokens(sentence, targetTokens)) {
							units.add(new Unit(page, heading, piece, tokens.count(piece)));
						}
					}
				}
			}
		}
		return units;
	}

	private List<Chunk> pack(List<Unit> units) {
		List<Chunk> chunks = new ArrayList<>();
		List<Unit> current = new ArrayList<>();
		int currentTokens = 0;
		int unitsSinceEmit = 0;
		for (Unit unit : units) {
			if (currentTokens + unit.tokens() > targetTokens && unitsSinceEmit > 0) {
				chunks.add(toChunk(chunks.size(), current));
				current = overlapTail(current);
				currentTokens = current.stream().mapToInt(Unit::tokens).sum();
				unitsSinceEmit = 0;
				// Drop overlap if it would not leave room for the next unit.
				while (!current.isEmpty() && currentTokens + unit.tokens() > targetTokens) {
					currentTokens -= current.removeFirst().tokens();
				}
			}
			current.add(unit);
			currentTokens += unit.tokens();
			unitsSinceEmit++;
		}
		if (unitsSinceEmit > 0) {
			chunks.add(toChunk(chunks.size(), current));
		}
		return chunks;
	}

	private List<Unit> overlapTail(List<Unit> units) {
		List<Unit> tail = new ArrayList<>();
		int total = 0;
		for (int i = units.size() - 1; i >= 0; i--) {
			Unit unit = units.get(i);
			if (total + unit.tokens() > overlapTokens) {
				break;
			}
			tail.addFirst(unit);
			total += unit.tokens();
		}
		return tail;
	}

	private static Chunk toChunk(int ordinal, List<Unit> units) {
		int pageStart = units.stream().mapToInt(Unit::page).min().orElseThrow();
		int pageEnd = units.stream().mapToInt(Unit::page).max().orElseThrow();
		String content = String.join("\n\n", units.stream().map(Unit::text).toList());
		int tokenCount = units.stream().mapToInt(Unit::tokens).sum();
		return new Chunk(ordinal, pageStart, pageEnd, units.getFirst().heading(), content, tokenCount);
	}

	static String headingOf(String paragraph) {
		if (paragraph.contains("\n")) {
			return null;
		}
		var markdown = MARKDOWN_HEADING.matcher(paragraph);
		if (markdown.matches()) {
			return truncate(markdown.group(1).strip());
		}
		if (paragraph.length() > 90 || paragraph.endsWith(".") || paragraph.endsWith(",") || paragraph.endsWith(":")) {
			return null;
		}
		boolean allCaps = paragraph.chars().anyMatch(Character::isLetter)
				&& paragraph.equals(paragraph.toUpperCase(Locale.ROOT)) && paragraph.length() >= 4;
		if (allCaps || NUMBERED_HEADING.matcher(paragraph).matches()) {
			return truncate(paragraph);
		}
		return null;
	}

	private static String truncate(String heading) {
		return heading.length() > 300 ? heading.substring(0, 300) : heading;
	}

	private static List<String> sentences(String text) {
		BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH);
		iterator.setText(text);
		List<String> sentences = new ArrayList<>();
		int start = iterator.first();
		for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
			String sentence = text.substring(start, end).strip();
			if (!sentence.isEmpty()) {
				sentences.add(sentence);
			}
		}
		return sentences;
	}

}
