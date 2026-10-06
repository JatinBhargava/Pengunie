package com.pengunie.auth;

import com.pengunie.support.ApiClient;
import com.pengunie.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthFlowIT {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	void registerThenReadAndUpdateProfile() throws Exception {
		var api = new ApiClient(mvc);
		var user = api.register();

		assertThat(user.refreshCookie()).isNotNull();
		assertThat(user.refreshCookie().isHttpOnly()).isTrue();
		mvc.perform(api.authed(get("/api/v1/me"), user))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(user.email()))
			.andExpect(jsonPath("$.timezone").value("Asia/Kolkata"));

		mvc.perform(api.authed(put("/api/v1/me"), user).contentType(MediaType.APPLICATION_JSON)
			.content("{\"displayName\":\"Asha\",\"timezone\":\"Europe/London\",\"locale\":\"en-GB\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Asha"))
			.andExpect(jsonPath("$.timezone").value("Europe/London"));

		mvc.perform(api.authed(put("/api/v1/me"), user).contentType(MediaType.APPLICATION_JSON)
			.content("{\"displayName\":\"Asha\",\"timezone\":\"Mars/Olympus\",\"locale\":\"en-GB\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_timezone"));
	}

	@Test
	void protectedEndpointsRequireToken() throws Exception {
		mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
	}

	@Test
	void duplicateEmailAndBadCredentials() throws Exception {
		var user = new ApiClient(mvc).register();

		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"another-long-pass\",\"displayName\":\"X\"}".formatted(
					user.email().toUpperCase())))
			.andExpect(status().isConflict());

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(user.email())))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("invalid_credentials"));

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"correct-horse-battery\"}".formatted(user.email())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty());

		long failedLogins = jdbc.sql("SELECT count(*) FROM audit_events WHERE actor_user_id = :id AND action = 'LOGIN_FAILED'")
			.param("id", user.id())
			.query(Long.class)
			.single();
		assertThat(failedLogins).isEqualTo(1);
	}

	@Test
	void refreshRotatesAndReuseRevokesTheFamily() throws Exception {
		var user = new ApiClient(mvc).register();
		Cookie original = user.refreshCookie();

		var refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(original))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andReturn();
		Cookie rotated = refreshed.getResponse().getCookie("pios_refresh");
		assertThat(rotated.getValue()).isNotEqualTo(original.getValue());

		// Replaying the already-rotated token looks like theft: it fails and kills the whole family...
		mvc.perform(post("/api/v1/auth/refresh").cookie(original))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
		// ...including the legitimately rotated token.
		mvc.perform(post("/api/v1/auth/refresh").cookie(rotated)).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesRefreshToken() throws Exception {
		var api = new ApiClient(mvc);
		var user = api.register();

		mvc.perform(api.authed(post("/api/v1/auth/logout"), user).cookie(user.refreshCookie()))
			.andExpect(status().isNoContent());
		mvc.perform(post("/api/v1/auth/refresh").cookie(user.refreshCookie())).andExpect(status().isUnauthorized());
	}

}
