package com.pengunie.ai;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Persists one row per model call for cost, latency and traceability (PRD §21). */
@Component
public class LlmCallRecorder {

	public record LlmCall(UUID userId, String purpose, String model, Integer inputTokens, Integer outputTokens,
			long latencyMs, boolean success, String error, String referenceType, UUID referenceId) {
	}

	private final JdbcClient jdbc;

	LlmCallRecorder(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public UUID record(LlmCall call) {
		return jdbc.sql("""
				INSERT INTO llm_calls (user_id, purpose, provider_model, input_tokens, output_tokens, latency_ms,
				                       success, error, reference_type, reference_id)
				VALUES (:user, :purpose, :model, :in, :out, :latency, :success, :error, :refType, :refId)
				RETURNING id
				""")
			.param("user", call.userId())
			.param("purpose", call.purpose())
			.param("model", call.model())
			.param("in", call.inputTokens())
			.param("out", call.outputTokens())
			.param("latency", (int) Math.min(call.latencyMs(), Integer.MAX_VALUE))
			.param("success", call.success())
			.param("error", call.error() == null ? null : truncate(call.error()))
			.param("refType", call.referenceType())
			.param("refId", call.referenceId())
			.query(UUID.class)
			.single();
	}

	private static String truncate(String error) {
		return error.length() > 2000 ? error.substring(0, 2000) : error;
	}

}
