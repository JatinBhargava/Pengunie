package com.pengunie.search;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import com.pengunie.jobs.JobWorker;
import com.pengunie.support.ApiClient;
import com.pengunie.support.IntegrationTest;
import com.pengunie.support.TestDocuments;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRD target: unauthorized data access = 0. One user's data must never reach another. */
@IntegrationTest
class ScopeIsolationIT {

	@Autowired
	MockMvc mvc;

	@Autowired
	JobWorker worker;

	@Test
	void searchFindsOwnPagesAndNeverAnotherUsersChunks() throws Exception {
		var api = new ApiClient(mvc);
		var owner = api.register();
		var stranger = api.register();
		byte[] pdf = TestDocuments.pdf(List.of(List.of("General notes about the household."),
				List.of("Passport number Z1234567 expires on 15 March 2031.",
						"Renew the passport at least six months before expiry.")));
		String docId = JsonPath.read(api.upload(owner, "ids.pdf", pdf).getResponse().getContentAsString(),
				"$.document.id");
		worker.drain();

		String semantic = api.postJson(owner, "/api/v1/search",
				"{\"query\":\"when does my passport expire\",\"mode\":\"SEMANTIC\",\"topK\":3}")
			.getResponse()
			.getContentAsString();
		assertThat((String) JsonPath.read(semantic, "$.results[0].documentId")).isEqualTo(docId);
		assertThat((String) JsonPath.read(semantic, "$.results[0].snippet")).contains("Passport");

		String keyword = api.postJson(owner, "/api/v1/search", "{\"query\":\"Z1234567\",\"mode\":\"KEYWORD\"}")
			.getResponse()
			.getContentAsString();
		assertThat((Integer) JsonPath.read(keyword, "$.results[0].pageStart")).isLessThanOrEqualTo(2);
		assertThat((Integer) JsonPath.read(keyword, "$.results[0].pageEnd")).isGreaterThanOrEqualTo(2);
		assertThat((String) JsonPath.read(keyword, "$.results[0].snippet")).contains("Z1234567");

		for (String mode : new String[] { "SEMANTIC", "KEYWORD" }) {
			String strangerResults = api.postJson(stranger, "/api/v1/search",
					"{\"query\":\"passport Z1234567 expires\",\"mode\":\"" + mode + "\"}")
				.getResponse()
				.getContentAsString();
			assertThat((List<?>) JsonPath.read(strangerResults, "$.results")).as(mode).isEmpty();
		}
		mvc.perform(api.authed(get("/api/v1/documents/" + docId), stranger)).andExpect(status().isNotFound());
		mvc.perform(api.authed(get("/api/v1/documents/" + docId + "/file"), stranger)).andExpect(status().isNotFound());
		assertThat(api.getJson(stranger, "/api/v1/documents/" + docId + "/chunks")).isEqualTo("[]");

		String chat = api.postJson(stranger, "/api/v1/chat", "{\"message\":\"When does the passport expire?\"}")
			.getResponse()
			.getContentAsString();
		assertThat((String) JsonPath.read(chat, "$.assistantMessage.grounding")).isEqualTo("NOT_FOUND");
		assertThat((List<?>) JsonPath.read(chat, "$.assistantMessage.citations")).isEmpty();
	}

}
