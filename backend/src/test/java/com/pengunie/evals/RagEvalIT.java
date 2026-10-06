package com.pengunie.evals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import com.pengunie.jobs.JobWorker;
import com.pengunie.support.ApiClient;
import com.pengunie.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ActiveProfilesResolver;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the curated RAG cases in {@code evals/rag/seed.jsonl} end to end and reports retrieval hit
 * rate, citation validity, decline rate and cross-user leakage (PRD §19).
 *
 * <p>Defaults to the deterministic fake models so it runs in every build. Against a real provider:
 * {@code ./mvnw verify -Dit.test=RagEvalIT -Deval.profile=openai -Deval.enforce=true} (needs the
 * provider's API key in the environment). Leakage is always enforced: the target is zero.
 */
@SpringBootTest(properties = "app.jobs.worker-enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles(resolver = RagEvalIT.EvalProfile.class)
class RagEvalIT {

	public static class EvalProfile implements ActiveProfilesResolver {

		@Override
		public String[] resolve(Class<?> testClass) {
			return new String[] { System.getProperty("eval.profile", "fake") };
		}

	}

	record CaseResult(String id, String type, boolean retrievalHit, boolean citedExpected, boolean declined,
			boolean leaked, boolean containsExpected, String grounding) {
	}

	private static final Path EVAL_DIR = Path.of("..", "evals", "rag");

	@Autowired
	MockMvc mvc;

	@Autowired
	JobWorker worker;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ObjectMapper json;

	@Test
	void ragEvaluation() throws Exception {
		var api = new ApiClient(mvc);
		var user = api.register();
		var otherUser = api.register();

		Map<String, String> documentIds = new HashMap<>();
		try (Stream<Path> docs = Files.list(EVAL_DIR.resolve("docs"))) {
			for (Path doc : docs.sorted().toList()) {
				String name = doc.getFileName().toString().replace(".md", "");
				var owner = name.startsWith("other-user") ? otherUser : user;
				String body = api.upload(owner, doc.getFileName().toString(), Files.readAllBytes(doc))
					.getResponse()
					.getContentAsString();
				documentIds.put(name, JsonPath.read(body, "$.document.id"));
			}
		}
		worker.drain();

		List<CaseResult> results = new ArrayList<>();
		for (String line : Files.readAllLines(EVAL_DIR.resolve("seed.jsonl"))) {
			if (line.isBlank()) {
				continue;
			}
			JsonNode testCase = json.readTree(line);
			String type = testCase.get("type").asString();
			String response = api.postJson(user, "/api/v1/chat",
					json.writeValueAsString(Map.of("message", testCase.get("question").asString())))
				.getResponse()
				.getContentAsString();
			String grounding = JsonPath.read(response, "$.assistantMessage.grounding");
			String content = JsonPath.read(response, "$.assistantMessage.content");
			List<String> citedDocs = JsonPath.read(response, "$.assistantMessage.citations[*].documentId");
			List<String> retrievedDocs = retrievedDocuments(JsonPath.read(response, "$.assistantMessage.id"));

			String expected = testCase.has("expectDocument") ? documentIds.get(testCase.get("expectDocument").asString())
					: null;
			String forbidden = testCase.has("forbiddenDocument")
					? documentIds.get(testCase.get("forbiddenDocument").asString()) : null;
			boolean containsExpected = true;
			if (testCase.has("expectContains")) {
				for (JsonNode needle : testCase.get("expectContains")) {
					containsExpected &= content.toLowerCase(Locale.ROOT).contains(needle.asString().toLowerCase(Locale.ROOT));
				}
			}
			results.add(new CaseResult(testCase.get("id").asString(), type,
					expected != null && retrievedDocs.contains(expected),
					"GROUNDED".equals(grounding) && expected != null && citedDocs.contains(expected),
					!"GROUNDED".equals(grounding),
					forbidden != null && (retrievedDocs.contains(forbidden) || citedDocs.contains(forbidden)),
					containsExpected, grounding));
		}

		var report = report(results);
		System.out.println(report.text());
		Files.writeString(Path.of("target", "rag-eval-report.json"), json.writerWithDefaultPrettyPrinter()
			.writeValueAsString(Map.of("profile", System.getProperty("eval.profile", "fake"), "metrics",
					report.metrics(), "cases", results)));

		assertThat(results).filteredOn(CaseResult::leaked).as("cross-user leakage (target: 0)").isEmpty();
		if (Boolean.getBoolean("eval.enforce")) {
			assertThat(report.metrics().get("retrievalHitRate")).as("retrieval hit rate").isGreaterThanOrEqualTo(0.85);
			assertThat(report.metrics().get("citationValidity")).as("valid citations").isGreaterThanOrEqualTo(0.90);
		}
	}

	private List<String> retrievedDocuments(String messageId) {
		String retrieval = jdbc.sql("SELECT retrieval::text FROM messages WHERE id = :id")
			.param("id", UUID.fromString(messageId))
			.query(String.class)
			.single();
		return JsonPath.read(retrieval, "$[*].documentId");
	}

	record Report(String text, Map<String, Double> metrics) {
	}

	private static Report report(List<CaseResult> results) {
		var answerable = results.stream().filter(r -> r.type().equals("answerable")).toList();
		var unanswerable = results.stream().filter(r -> r.type().equals("unanswerable")).toList();
		var denied = results.stream().filter(r -> r.type().equals("denied")).toList();
		Map<String, Double> metrics = new java.util.LinkedHashMap<>();
		metrics.put("retrievalHitRate", rate(answerable.stream().filter(CaseResult::retrievalHit).count(), answerable.size()));
		metrics.put("citationValidity", rate(answerable.stream().filter(CaseResult::citedExpected).count(), answerable.size()));
		metrics.put("answerContainsExpected",
				rate(answerable.stream().filter(r -> r.citedExpected() && r.containsExpected()).count(), answerable.size()));
		metrics.put("unanswerableDeclined", rate(unanswerable.stream().filter(CaseResult::declined).count(), unanswerable.size()));
		metrics.put("deniedDeclined", rate(denied.stream().filter(CaseResult::declined).count(), denied.size()));
		metrics.put("leakedCases", (double) results.stream().filter(CaseResult::leaked).count());

		StringBuilder text = new StringBuilder("\n===== RAG evaluation (" + System.getProperty("eval.profile", "fake")
				+ ", " + results.size() + " cases) =====\n");
		for (CaseResult r : results) {
			text.append(String.format("%-7s %-12s %-10s retrieved=%-5s cited=%-5s leaked=%s%n", r.id(), r.type(),
					r.grounding(), r.retrievalHit(), r.citedExpected(), r.leaked()));
		}
		metrics.forEach((k, v) -> text.append(String.format("%-24s %s%n", k,
				k.equals("leakedCases") ? String.valueOf(v.intValue()) : String.format("%.0f%%", v * 100))));
		return new Report(text.toString(), metrics);
	}

	private static double rate(long hits, int total) {
		return total == 0 ? 1.0 : (double) hits / total;
	}

}
