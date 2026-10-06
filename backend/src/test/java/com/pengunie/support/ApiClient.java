package com.pengunie.support;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Small helper for driving the API as a registered user. */
public final class ApiClient {

	public record User(UUID id, String email, String accessToken, Cookie refreshCookie) {
	}

	private final MockMvc mvc;

	public ApiClient(MockMvc mvc) {
		this.mvc = mvc;
	}

	public User register() throws Exception {
		String email = "user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email":"%s","password":"correct-horse-battery","displayName":"Test User","timezone":"Asia/Kolkata"}
					""".formatted(email)))
			.andReturn();
		if (result.getResponse().getStatus() != 201) {
			throw new IllegalStateException("register failed: " + result.getResponse().getContentAsString());
		}
		String body = result.getResponse().getContentAsString();
		return new User(UUID.fromString(JsonPath.read(body, "$.user.id")), email, JsonPath.read(body, "$.accessToken"),
				result.getResponse().getCookie("pios_refresh"));
	}

	public <B extends AbstractMockHttpServletRequestBuilder<B>> B authed(B request, User user) {
		return request.header("Authorization", "Bearer " + user.accessToken());
	}

	public MvcResult upload(User user, String filename, byte[] content) throws Exception {
		return mvc.perform(authed(multipart("/api/v1/documents").file(new MockMultipartFile("file", filename,
				"application/octet-stream", content)), user)).andReturn();
	}

	public String getJson(User user, String path) throws Exception {
		return mvc.perform(authed(get(path), user)).andReturn().getResponse().getContentAsString();
	}

	public MvcResult postJson(User user, String path, String json) throws Exception {
		return mvc.perform(authed(post(path), user).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
	}

}
