package com.pengunie.search;

import java.util.ArrayList;
import java.util.List;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;

import org.springframework.stereotype.Component;

/**
 * Token counts for chunk sizing. cl100k is an approximation for non-OpenAI models, which is fine:
 * it only has to keep chunks within a predictable size band.
 */
@Component
public class TokenCounter {

	private final Encoding encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

	public int count(String text) {
		return encoding.countTokensOrdinary(text);
	}

	/** Splits text into pieces of at most {@code maxTokens} tokens. */
	public List<String> splitByTokens(String text, int maxTokens) {
		IntArrayList tokens = encoding.encodeOrdinary(text);
		List<String> pieces = new ArrayList<>();
		for (int start = 0; start < tokens.size(); start += maxTokens) {
			IntArrayList slice = new IntArrayList(maxTokens);
			for (int i = start; i < Math.min(start + maxTokens, tokens.size()); i++) {
				slice.add(tokens.get(i));
			}
			pieces.add(encoding.decode(slice));
		}
		return pieces;
	}

}
