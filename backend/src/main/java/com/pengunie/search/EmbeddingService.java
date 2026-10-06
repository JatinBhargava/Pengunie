package com.pengunie.search;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pengunie.ai.LlmCallRecorder;
import com.pengunie.ai.LlmCallRecorder.LlmCall;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

/** Provider-agnostic embeddings with batching and per-call cost recording. */
@Service
public class EmbeddingService {

	public record Embedded(List<float[]> vectors, String model) {
	}

	private final EmbeddingModel model;

	private final LlmCallRecorder recorder;

	private final EmbeddingProperties props;

	EmbeddingService(EmbeddingModel model, LlmCallRecorder recorder, EmbeddingProperties props) {
		this.model = model;
		this.recorder = recorder;
		this.props = props;
	}

	public Embedded embedAll(List<String> texts, UUID userId, String purpose, UUID documentId) {
		List<float[]> vectors = new ArrayList<>(texts.size());
		String modelName = null;
		for (int start = 0; start < texts.size(); start += props.batchSize()) {
			List<String> batch = texts.subList(start, Math.min(start + props.batchSize(), texts.size()));
			EmbeddingResponse response = call(batch, userId, purpose, "document", documentId);
			response.getResults().forEach(e -> vectors.add(checkDimensions(e.getOutput())));
			modelName = modelName(response);
		}
		return new Embedded(vectors, modelName);
	}

	public float[] embedQuery(String text, UUID userId, String purpose, String referenceType, UUID referenceId) {
		EmbeddingResponse response = call(List.of(text), userId, purpose, referenceType, referenceId);
		return checkDimensions(response.getResult().getOutput());
	}

	private EmbeddingResponse call(List<String> texts, UUID userId, String purpose, String refType, UUID refId) {
		long started = System.nanoTime();
		try {
			EmbeddingResponse response = model.call(new EmbeddingRequest(texts, null));
			var usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
			recorder.record(new LlmCall(userId, purpose, modelName(response),
					usage == null ? null : usage.getPromptTokens(), null, elapsedMs(started), true, null, refType,
					refId));
			return response;
		}
		catch (RuntimeException ex) {
			recorder.record(new LlmCall(userId, purpose, null, null, null, elapsedMs(started), false, ex.getMessage(),
					refType, refId));
			throw ex;
		}
	}

	private float[] checkDimensions(float[] vector) {
		if (vector.length != props.dimensions()) {
			throw new IllegalStateException("Embedding model returned " + vector.length + " dimensions but the "
					+ "schema expects " + props.dimensions() + " (app.embedding.dimensions). See ADR 0003.");
		}
		return vector;
	}

	private String modelName(EmbeddingResponse response) {
		String name = response.getMetadata() == null ? null : response.getMetadata().getModel();
		return name == null || name.isBlank() ? model.getClass().getSimpleName() : name;
	}

	private static long elapsedMs(long startedNanos) {
		return (System.nanoTime() - startedNanos) / 1_000_000;
	}

	static String toPgVector(float[] vector) {
		StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
		for (int i = 0; i < vector.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(vector[i]);
		}
		return sb.append(']').toString();
	}

}
