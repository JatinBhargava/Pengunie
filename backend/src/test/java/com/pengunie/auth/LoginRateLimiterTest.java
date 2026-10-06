package com.pengunie.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

	@Test
	void limitsPerKeyPerMinute() {
		var limiter = new LoginRateLimiter(3, Clock.fixed(Instant.parse("2026-10-02T10:00:10Z"), ZoneOffset.UTC));

		assertThat(limiter.tryAcquire("a")).isTrue();
		assertThat(limiter.tryAcquire("a")).isTrue();
		assertThat(limiter.tryAcquire("a")).isTrue();
		assertThat(limiter.tryAcquire("a")).isFalse();
		assertThat(limiter.tryAcquire("b")).isTrue();
	}

	@Test
	void windowResetsNextMinute() {
		var clock = new MutableClock(Instant.parse("2026-10-02T10:00:10Z"));
		var limiter = new LoginRateLimiter(1, clock);

		assertThat(limiter.tryAcquire("a")).isTrue();
		assertThat(limiter.tryAcquire("a")).isFalse();
		clock.now = Instant.parse("2026-10-02T10:01:00Z");
		assertThat(limiter.tryAcquire("a")).isTrue();
	}

	static class MutableClock extends Clock {

		Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
