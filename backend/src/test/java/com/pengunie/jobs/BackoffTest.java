package com.pengunie.jobs;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffTest {

	@Test
	void growsExponentiallyAndIsCapped() {
		assertThat(Backoff.afterAttempt(1)).isEqualTo(Duration.ofSeconds(5));
		assertThat(Backoff.afterAttempt(2)).isEqualTo(Duration.ofSeconds(10));
		assertThat(Backoff.afterAttempt(4)).isEqualTo(Duration.ofSeconds(40));
		assertThat(Backoff.afterAttempt(10)).isEqualTo(Duration.ofMinutes(10));
		assertThat(Backoff.afterAttempt(500)).isEqualTo(Duration.ofMinutes(10));
	}

}
