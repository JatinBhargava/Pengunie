package com.pengunie.auth;

import java.util.Map;
import java.util.UUID;

import com.pengunie.audit.AuditService;
import com.pengunie.common.ApiException;
import com.pengunie.users.Profile;
import com.pengunie.users.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {

	record Session(TokenService.AccessToken accessToken, String refreshToken, Profile user) {
	}


	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokens;

	private final LoginRateLimiter rateLimiter;

	private final AuditService audit;

	// Compared against when the email is unknown so that response timing does not reveal accounts.
	private final String dummyHash;

	AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
			LoginRateLimiter rateLimiter, AuditService audit) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
		this.rateLimiter = rateLimiter;
		this.audit = audit;
		this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Transactional
	Session register(String email, String password, String displayName, String timezone, String ip) {
		String normalizedEmail = email.strip().toLowerCase();
		if (users.emailExists(normalizedEmail)) {
			throw ApiException.conflict("email_taken", "An account with this email already exists");
		}
		UUID userId = users.create(normalizedEmail, passwordEncoder.encode(password), displayName.strip(), timezone);
		audit.record(userId, "USER_REGISTERED", "user", userId, Map.of(), ip);
		return openSession(userId);
	}

	// Failed attempts must still leave an audit row, so rejections do not roll back.
	@Transactional(noRollbackFor = ApiException.class)
	Session login(String email, String password, String ip) {
		String normalizedEmail = email.strip().toLowerCase();
		if (!rateLimiter.tryAcquire(ip + "|" + normalizedEmail)) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited",
					"Too many login attempts, try again in a minute");
		}
		var credentials = users.findCredentialsByEmail(normalizedEmail);
		boolean matches = passwordEncoder.matches(password,
				credentials.map(UserRepository.UserCredentials::passwordHash).orElse(dummyHash));
		if (credentials.isEmpty() || !matches || !"ACTIVE".equals(credentials.get().status())) {
			audit.record(credentials.map(UserRepository.UserCredentials::id).orElse(null), "LOGIN_FAILED", "user",
					null, Map.of("email", normalizedEmail), ip);
			throw ApiException.unauthorized("invalid_credentials", "Invalid email or password");
		}
		UUID userId = credentials.get().id();
		audit.record(userId, "LOGIN_SUCCEEDED", "user", userId, Map.of(), ip);
		return openSession(userId);
	}

	@Transactional(noRollbackFor = ApiException.class)
	Session refresh(String refreshToken) {
		TokenService.IssuedRefreshToken rotated = tokens.rotate(refreshToken);
		Profile profile = users.findProfile(rotated.userId()).orElseThrow(() -> ApiException.notFound("User"));
		return new Session(tokens.issueAccessToken(profile.id(), profile.email()), rotated.value(), profile);
	}

	@Transactional
	void logout(String refreshToken, UUID userId, String ip) {
		tokens.revoke(refreshToken);
		audit.record(userId, "LOGOUT", "user", userId, Map.of(), ip);
	}

	private Session openSession(UUID userId) {
		Profile profile = users.findProfile(userId).orElseThrow();
		return new Session(tokens.issueAccessToken(userId, profile.email()), tokens.issueRefreshToken(userId), profile);
	}

}
