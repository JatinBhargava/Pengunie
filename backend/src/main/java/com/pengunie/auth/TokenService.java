package com.pengunie.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import com.pengunie.common.ApiException;
import com.pengunie.common.Hashing;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TokenService {

	record AccessToken(String value, long expiresInSeconds) {
	}

	record IssuedRefreshToken(String value, UUID userId) {
	}

	private final JwtEncoder encoder;

	private final JdbcClient jdbc;

	private final AuthProperties props;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	TokenService(JwtEncoder encoder, JdbcClient jdbc, AuthProperties props) {
		this.encoder = encoder;
		this.jdbc = jdbc;
		this.props = props;
		this.clock = Clock.systemUTC();
	}

	AccessToken issueAccessToken(UUID userId, String email) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(props.issuer())
			.subject(userId.toString())
			.issuedAt(now)
			.expiresAt(now.plus(props.accessTokenTtl()))
			.id(UUID.randomUUID().toString())
			.claim("email", email)
			.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(JwtConfig.KEY_ID).build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(token, props.accessTokenTtl().toSeconds());
	}

	/** Starts a new refresh-token family (a new login). */
	@Transactional
	String issueRefreshToken(UUID userId) {
		return insertRefreshToken(userId, UUID.randomUUID());
	}

	/**
	 * Exchanges a refresh token for a new one in the same family. Presenting a token that was already
	 * rotated or revoked means it leaked: the whole family is revoked and the user must log in again.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	IssuedRefreshToken rotate(String rawToken) {
		record Row(UUID id, UUID userId, UUID familyId, OffsetDateTime expiresAt, OffsetDateTime rotatedAt,
				OffsetDateTime revokedAt) {
		}
		Row row = jdbc.sql("""
				SELECT id, user_id, family_id, expires_at, rotated_at, revoked_at
				FROM refresh_tokens WHERE token_hash = :hash FOR UPDATE
				""")
			.param("hash", hash(rawToken))
			.query(Row.class)
			.optional()
			.orElseThrow(TokenService::invalid);
		if (row.rotatedAt() != null || row.revokedAt() != null) {
			revokeFamily(row.familyId());
			throw invalid();
		}
		if (row.expiresAt().toInstant().isBefore(clock.instant())) {
			throw invalid();
		}
		jdbc.sql("UPDATE refresh_tokens SET rotated_at = now() WHERE id = :id").param("id", row.id()).update();
		return new IssuedRefreshToken(insertRefreshToken(row.userId(), row.familyId()), row.userId());
	}

	@Transactional
	void revoke(String rawToken) {
		jdbc.sql("SELECT family_id FROM refresh_tokens WHERE token_hash = :hash")
			.param("hash", hash(rawToken))
			.query(UUID.class)
			.optional()
			.ifPresent(this::revokeFamily);
	}

	private void revokeFamily(UUID familyId) {
		jdbc.sql("UPDATE refresh_tokens SET revoked_at = now() WHERE family_id = :family AND revoked_at IS NULL")
			.param("family", familyId)
			.update();
	}

	private String insertRefreshToken(UUID userId, UUID familyId) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		jdbc.sql("""
				INSERT INTO refresh_tokens (user_id, family_id, token_hash, expires_at)
				VALUES (:user, :family, :hash, :expires)
				""")
			.param("user", userId)
			.param("family", familyId)
			.param("hash", hash(raw))
			.param("expires", OffsetDateTime.ofInstant(clock.instant().plus(props.refreshTokenTtl()), ZoneOffset.UTC))
			.update();
		return raw;
	}

	private static String hash(String raw) {
		return Hashing.sha256Hex(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private static ApiException invalid() {
		return ApiException.unauthorized("invalid_refresh_token", "Refresh token is invalid or expired");
	}

}
