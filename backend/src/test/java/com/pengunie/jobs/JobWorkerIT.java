package com.pengunie.jobs;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.pengunie.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Import(JobWorkerIT.TestHandlers.class)
class JobWorkerIT {

	static final Map<String, AtomicInteger> EXECUTIONS = new ConcurrentHashMap<>();

	@TestConfiguration
	static class TestHandlers {

		@Bean
		JobHandler countingHandler() {
			return new JobHandler() {
				@Override
				public String type() {
					return "TEST_COUNT";
				}

				@Override
				public void handle(JsonNode payload) throws Exception {
					EXECUTIONS.computeIfAbsent(payload.get("key").asString(), k -> new AtomicInteger()).incrementAndGet();
					Thread.sleep(5);
				}
			};
		}

		@Bean
		JobHandler failingHandler() {
			return new JobHandler() {
				@Override
				public String type() {
					return "TEST_FAIL";
				}

				@Override
				public void handle(JsonNode payload) {
					throw new IllegalStateException("boom");
				}
			};
		}

	}

	@Autowired
	JobQueue queue;

	@Autowired
	JobWorker worker;

	@Autowired
	JdbcClient jdbc;

	@Test
	void concurrentWorkersNeverRunTheSameJobTwice() throws Exception {
		String batch = UUID.randomUUID().toString();
		Set<String> keys = ConcurrentHashMap.newKeySet();
		for (int i = 0; i < 40; i++) {
			String key = batch + "-" + i;
			keys.add(key);
			queue.enqueue("TEST_COUNT", Map.of("key", key), "test:" + key);
		}

		try (ExecutorService pool = Executors.newFixedThreadPool(6)) {
			for (int i = 0; i < 6; i++) {
				pool.submit(() -> worker.drain());
			}
		}

		assertThat(keys).allSatisfy(key -> assertThat(EXECUTIONS.get(key)).as(key).hasValue(1));
	}

	@Test
	void idempotencyKeyPreventsDuplicateJobs() {
		String key = "dedupe:" + UUID.randomUUID();

		assertThat(queue.enqueue("TEST_COUNT", Map.of("key", key), key)).isPresent();
		assertThat(queue.enqueue("TEST_COUNT", Map.of("key", key), key)).isEmpty();
	}

	@Test
	void failuresBackOffThenGiveUpAfterMaxAttempts() {
		UUID id = queue.enqueue("TEST_FAIL", Map.of(), "fail:" + UUID.randomUUID()).orElseThrow();
		jdbc.sql("UPDATE jobs SET max_attempts = 2 WHERE id = :id").param("id", id).update();

		worker.drain();
		var afterFirst = jdbc.sql("SELECT status, attempts, run_at > now() AS deferred FROM jobs WHERE id = :id")
			.param("id", id)
			.query((rs, n) -> Map.of("status", rs.getString(1), "attempts", rs.getInt(2), "deferred", rs.getBoolean(3)))
			.single();
		assertThat(afterFirst).containsEntry("status", "QUEUED").containsEntry("attempts", 1)
			.containsEntry("deferred", true);

		jdbc.sql("UPDATE jobs SET run_at = now() WHERE id = :id").param("id", id).update();
		worker.drain();
		String status = jdbc.sql("SELECT status FROM jobs WHERE id = :id").param("id", id).query(String.class).single();
		assertThat(status).isEqualTo("FAILED");
	}

}
