package com.pengunie.jobs;

import java.time.Duration;

/** Bounded exponential backoff: 5s, 10s, 20s, ... capped at 10 minutes. */
final class Backoff {

	static final Duration BASE = Duration.ofSeconds(5);

	static final Duration MAX = Duration.ofMinutes(10);

	private Backoff() {
	}

	static Duration afterAttempt(int attempt) {
		int exponent = Math.clamp(attempt - 1, 0, 20);
		long seconds = BASE.toSeconds() << exponent;
		return seconds >= MAX.toSeconds() ? MAX : Duration.ofSeconds(seconds);
	}

}
