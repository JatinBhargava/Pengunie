package com.pengunie.auth;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fixed-window limiter for login attempts per client IP + email. In-memory is enough for a single
 * instance; move to Postgres or Redis when the API is scaled out.
 */
@Component
class LoginRateLimiter {

	private record Window(long minute, int count) {
	}

	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	private final int limitPerMinute;

	private final Clock clock;

	@Autowired
	LoginRateLimiter(AuthProperties props) {
		this(props.loginAttemptsPerMinute(), Clock.systemUTC());
	}

	LoginRateLimiter(int limitPerMinute, Clock clock) {
		this.limitPerMinute = limitPerMinute;
		this.clock = clock;
	}

	/** Records an attempt and returns whether it is allowed. */
	boolean tryAcquire(String key) {
		long minute = clock.millis() / 60_000;
		Window window = windows.merge(key, new Window(minute, 1),
				(old, fresh) -> old.minute() == minute ? new Window(minute, old.count() + 1) : fresh);
		return window.count() <= limitPerMinute;
	}

	@Scheduled(fixedDelay = 300_000)
	void evictStale() {
		long minute = clock.millis() / 60_000;
		windows.values().removeIf(w -> w.minute() < minute);
	}

}
