package com.pengunie.jobs;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class JobQueue {

	private final JdbcClient jdbc;

	private final ObjectMapper json;

	JobQueue(JdbcClient jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	/**
	 * Enqueues a job in the caller's transaction, so a job exists if and only if the business
	 * change that requires it commits. Returns empty if a job with the same idempotency key exists.
	 */
	public Optional<UUID> enqueue(String type, Object payload, String idempotencyKey) {
		return jdbc.sql("""
				INSERT INTO jobs (type, payload, idempotency_key) VALUES (:type, CAST(:payload AS jsonb), :key)
				ON CONFLICT (idempotency_key) DO NOTHING RETURNING id
				""")
			.param("type", type)
			.param("payload", json.writeValueAsString(payload))
			.param("key", idempotencyKey)
			.query(UUID.class)
			.optional();
	}

}
