package com.pengunie.jobs;

import tools.jackson.databind.JsonNode;

/** Executes one job type. Handlers must be idempotent: a job can run more than once. */
public interface JobHandler {

	String type();

	void handle(JsonNode payload) throws Exception;

	/** Called once when the job is given up on (permanent failure or attempts exhausted). */
	default void onGiveUp(JsonNode payload, Exception error) {
	}

}
