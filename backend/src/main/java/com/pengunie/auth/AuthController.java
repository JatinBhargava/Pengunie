package com.pengunie.auth;

import java.util.UUID;

import com.pengunie.common.ApiException;
import com.pengunie.common.Requests;
import com.pengunie.users.Profile;
import com.pengunie.users.Timezones;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	static final String REFRESH_COOKIE = "pios_refresh";

	private final AuthService auth;

	private final AuthProperties props;

	AuthController(AuthService auth, AuthProperties props) {
		this.auth = auth;
		this.props = props;
	}

	record RegisterRequest(@NotBlank @Email @Size(max = 320) String email,
			@NotBlank @Size(min = 10, max = 128) String password, @NotBlank @Size(max = 120) String displayName,
			String timezone) {
	}

	record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	record SessionResponse(String accessToken, String tokenType, long expiresIn, Profile user) {
	}

	@PostMapping("/register")
	ResponseEntity<SessionResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
		String timezone = request.timezone() == null || request.timezone().isBlank() ? "UTC"
				: Timezones.validate(request.timezone());
		var session = auth.register(request.email(), request.password(), request.displayName(), timezone,
				Requests.clientIp(http));
		return respond(HttpStatus.CREATED, session);
	}

	@PostMapping("/login")
	ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return respond(HttpStatus.OK, auth.login(request.email(), request.password(), Requests.clientIp(http)));
	}

	@PostMapping("/refresh")
	ResponseEntity<SessionResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
		if (token == null || token.isBlank()) {
			throw ApiException.unauthorized("missing_refresh_token", "No refresh token");
		}
		try {
			return respond(HttpStatus.OK, auth.refresh(token));
		}
		catch (ApiException ex) {
			return ResponseEntity.status(ex.status()).header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
		}
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
		if (token != null && !token.isBlank()) {
			auth.logout(token, jwt == null ? null : UUID.fromString(jwt.getSubject()), Requests.clientIp(http));
		}
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
	}

	private ResponseEntity<SessionResponse> respond(HttpStatus status, AuthService.Session session) {
		ResponseCookie cookie = refreshCookie(session.refreshToken(), props.refreshTokenTtl().toSeconds());
		return ResponseEntity.status(status)
			.header(HttpHeaders.SET_COOKIE, cookie.toString())
			.body(new SessionResponse(session.accessToken().value(), "Bearer",
					session.accessToken().expiresInSeconds(), session.user()));
	}

	private ResponseCookie clearCookie() {
		return refreshCookie("", 0);
	}

	private ResponseCookie refreshCookie(String value, long maxAgeSeconds) {
		return ResponseCookie.from(REFRESH_COOKIE, value)
			.httpOnly(true)
			.secure(props.refreshCookieSecure())
			.sameSite("Strict")
			.path("/api/v1/auth")
			.maxAge(maxAgeSeconds)
			.build();
	}

}
