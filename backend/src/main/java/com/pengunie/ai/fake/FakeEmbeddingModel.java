package com.pengunie.ai.fake;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

/**
 * Deterministic feature-hashing embeddings: texts that share words get similar vectors. Good enough
 * to exercise the whole pipeline offline and in CI; not a substitute for a real model's quality.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

	public static final String MODEL = "fake-embedding";

	private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

	private final int dimensions;

	public FakeEmbeddingModel(int dimensions) {
		this.dimensions = dimensions;
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		List<Embedding> embeddings = new ArrayList<>();
		int tokens = 0;
		for (int i = 0; i < request.getInstructions().size(); i++) {
			String text = request.getInstructions().get(i);
			embeddings.add(new Embedding(vector(text), i));
			tokens += Math.max(1, text.length() / 4);
		}
		return new EmbeddingResponse(embeddings, new EmbeddingResponseMetadata(MODEL, new DefaultUsage(tokens, 0)));
	}

	@Override
	public float[] embed(Document document) {
		return vector(document.getText());
	}

	@Override
	public int dimensions() {
		return dimensions;
	}

	float[] vector(String text) {
		float[] v = new float[dimensions];
		var matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
		while (matcher.find()) {
			String word = matcher.group();
			if (word.length() < 3 || FakeText.STOPWORDS.contains(word)) {
				continue;
			}
			String stem = FakeText.stem(word);
			int hash = stem.hashCode();
			v[Math.floorMod(hash, dimensions)] += (hash & 1) == 0 ? 1f : -1f;
		}
		double norm = 0;
		for (float x : v) {
			norm += x * x;
		}
		if (norm == 0) {
			v[0] = 1f;
			return v;
		}
		float scale = (float) (1 / Math.sqrt(norm));
		for (int i = 0; i < v.length; i++) {
			v[i] *= scale;
		}
		return v;
	}

}
