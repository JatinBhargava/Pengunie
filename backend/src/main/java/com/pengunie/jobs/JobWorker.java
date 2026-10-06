package com.pengunie.jobs;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Polls the jobs table. Claiming is a single UPDATE ... WHERE id IN (SELECT ... FOR UPDATE SKIP
 * LOCKED), so concurrent workers (threads or instances) never claim the same job.
 */
@Component
public class JobWorker {

	private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

	record ClaimedJob(UUID id, String type, String payload, int attempts, int maxAttempts) {
	}

	private final JdbcClient jdbc;

	private final ObjectMapper json;

	private final JobProperties props;

	private final Map<String, JobHandler> handlers;

	private final String workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	JobWorker(JdbcClient jdbc, ObjectMapper json, JobProperties props, List<JobHandler> handlers) {
		this.jdbc = jdbc;
		this.json = json;
		this.props = props;
		this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
	}

	@Scheduled(fixedDelayString = "${app.jobs.poll-interval}", initialDelay = 2000)
	void poll() {
		if (props.workerEnabled()) {
			processAvailable();
		}
	}

	/** Claims and runs one batch; returns how many jobs were processed. */
	public int processAvailable() {
		List<ClaimedJob> claimed = claim(props.batchSize());
		List<Future<?>> running = new ArrayList<>();
		for (ClaimedJob job : claimed) {
			running.add(executor.submit(() -> execute(job)));
		}
		for (Future<?> future : running) {
			try {
				future.get();
			}
			catch (Exception ex) {
				log.error("Job execution wrapper failed", ex);
			}
		}
		return claimed.size();
	}

	/** Processes until the queue has no runnable jobs left (used by tests and tooling). */
	public void drain() {
		while (processAvailable() > 0) {
			// keep going
		}
	}

	List<ClaimedJob> claim(int limit) {
		return jdbc.sql("""
				UPDATE jobs SET status = 'RUNNING', attempts = attempts + 1, locked_by = :worker,
				       locked_until = now() + CAST(:lease AS interval), updated_at = now()
				WHERE id IN (
				    SELECT id FROM jobs
				    WHERE (status = 'QUEUED' AND run_at <= now()) OR (status = 'RUNNING' AND locked_until < now())
				    ORDER BY run_at
				    LIMIT :limit
				    FOR UPDATE SKIP LOCKED)
				RETURNING id, type, payload::text AS payload, attempts, max_attempts
				""")
			.param("worker", workerId)
			.param("lease", props.lease().toSeconds() + " seconds")
			.param("limit", limit)
			.query(ClaimedJob.class)
			.list();
	}

	private void execute(ClaimedJob job) {
		JobHandler handler = handlers.get(job.type());
		JsonNode payload = json.readTree(job.payload());
		if (handler == null) {
			fail(job, null, payload, new PermanentJobFailure("No handler for job type " + job.type()));
			return;
		}
		try {
			handler.handle(payload);
			jdbc.sql("UPDATE jobs SET status = 'DONE', locked_until = NULL, last_error = NULL, updated_at = now() "
					+ "WHERE id = :id")
				.param("id", job.id())
				.update();
		}
		catch (Exception ex) {
			fail(job, handler, payload, ex);
		}
	}

	private void fail(ClaimedJob job, JobHandler handler, JsonNode payload, Exception ex) {
		boolean giveUp = ex instanceof PermanentJobFailure || job.attempts() >= job.maxAttempts();
		String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
		if (giveUp) {
			log.warn("Job {} ({}) failed permanently after {} attempt(s): {}", job.id(), job.type(), job.attempts(),
					error);
			jdbc.sql("UPDATE jobs SET status = 'FAILED', locked_until = NULL, last_error = :error, updated_at = now() "
					+ "WHERE id = :id")
				.param("id", job.id())
				.param("error", error)
				.update();
			if (handler != null) {
				try {
					handler.onGiveUp(payload, ex);
				}
				catch (Exception callbackError) {
					log.error("onGiveUp failed for job {}", job.id(), callbackError);
				}
			}
			return;
		}
		Duration delay = Backoff.afterAttempt(job.attempts());
		log.info("Job {} ({}) attempt {} failed, retrying in {}: {}", job.id(), job.type(), job.attempts(), delay,
				error);
		jdbc.sql("""
				UPDATE jobs SET status = 'QUEUED', locked_until = NULL, last_error = :error,
				       run_at = now() + CAST(:delay AS interval), updated_at = now()
				WHERE id = :id
				""")
			.param("id", job.id())
			.param("error", error)
			.param("delay", delay.toSeconds() + " seconds")
			.update();
	}

	@PreDestroy
	void shutdown() {
		executor.shutdown();
	}

}
