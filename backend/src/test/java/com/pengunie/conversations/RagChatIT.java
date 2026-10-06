package com.pengunie.conversations;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import com.pengunie.jobs.JobWorker;
import com.pengunie.support.ApiClient;
import com.pengunie.support.IntegrationTest;
import com.pengunie.support.TestDocuments;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class RagChatIT {

	@Autowired
	MockMvc mvc;

	@Autowired
	JobWorker worker;

	@Autowired
	JdbcClient jdbc;

	@Test
	void answersWithValidatedPageCitationsAndDeclinesUnrelatedQuestions() throws Exception {
		var api = new ApiClient(mvc);
		var user = api.register();
		byte[] pdf = TestDocuments.pdf(List.of(
				List.of("Household handbook.", "This handbook collects family paperwork."),
				List.of("The family doctor is Dr. Mehta at Lotus Clinic.",
						"Annual health checkups are scheduled every November."),
				List.of("The home loan EMI of 42,000 rupees is debited on the 5th of every month.")));
		api.upload(user, "handbook.pdf", pdf);
		worker.drain();

		String first = api.postJson(user, "/api/v1/chat",
				"{\"message\":\"Who is our family doctor and which clinic?\"}").getResponse().getContentAsString();
		assertThat((String) JsonPath.read(first, "$.assistantMessage.grounding")).isEqualTo("GROUNDED");
		assertThat((String) JsonPath.read(first, "$.assistantMessage.content")).contains("Mehta");
		assertThat((Integer) JsonPath.read(first, "$.assistantMessage.citations[0].pageStart")).isEqualTo(2);
		assertThat((String) JsonPath.read(first, "$.assistantMessage.citations[0].documentTitle")).isEqualTo("handbook");
		String conversationId = JsonPath.read(first, "$.conversationId");

		String unrelated = api.postJson(user, "/api/v1/chat",
				"{\"conversationId\":\"%s\",\"message\":\"What is the capital of Peru?\"}".formatted(conversationId))
			.getResponse()
			.getContentAsString();
		assertThat((String) JsonPath.read(unrelated, "$.assistantMessage.grounding")).isEqualTo("NOT_FOUND");
		assertThat((List<?>) JsonPath.read(unrelated, "$.assistantMessage.citations")).isEmpty();

		mvc.perform(api.authed(get("/api/v1/conversations/" + conversationId), user))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.messages.length()").value(4))
			.andExpect(jsonPath("$.messages[1].citations[0].quote").isNotEmpty());

		long ragCalls = jdbc.sql("SELECT count(*) FROM llm_calls WHERE user_id = :u AND purpose = 'rag_answer'")
			.param("u", user.id())
			.query(Long.class)
			.single();
		assertThat(ragCalls).isGreaterThanOrEqualTo(1);
	}

	@Test
	void cannotPostIntoAnotherUsersConversation() throws Exception {
		var api = new ApiClient(mvc);
		var owner = api.register();
		var stranger = api.register();
		String conversationId = JsonPath.read(api.postJson(owner, "/api/v1/chat", "{\"message\":\"hello there\"}")
			.getResponse()
			.getContentAsString(), "$.conversationId");

		var result = api.postJson(stranger, "/api/v1/chat",
				"{\"conversationId\":\"%s\",\"message\":\"hi\"}".formatted(conversationId));

		assertThat(result.getResponse().getStatus()).isEqualTo(404);
		mvc.perform(api.authed(get("/api/v1/conversations/" + conversationId), stranger))
			.andExpect(status().isNotFound());
	}

}
