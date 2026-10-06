package com.pengunie.common;

import java.util.Objects;
import java.util.UUID;

/**
 * The data boundary a request is allowed to read. Every retrieval query takes a scope and filters
 * on it in SQL, so unauthorized rows never reach application code or LLM context.
 *
 * <p>Weeks 1–4: a user can only see their own data. Week 6 adds family workspace ids and domain
 * (health/finance) grants.
 */
public record AccessScope(UUID userId) {

	public AccessScope {
		Objects.requireNonNull(userId, "userId");
	}

	public static AccessScope ofUser(UUID userId) {
		return new AccessScope(userId);
	}

}
