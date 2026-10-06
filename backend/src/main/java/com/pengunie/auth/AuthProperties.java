package com.pengunie.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(String issuer, Duration accessTokenTtl, Duration refreshTokenTtl, String privateKey,
		String publicKey, boolean refreshCookieSecure, int loginAttemptsPerMinute) {
}
