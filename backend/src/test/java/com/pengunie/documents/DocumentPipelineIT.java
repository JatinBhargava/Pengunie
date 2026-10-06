package com.pengunie.documents;

import java.nio.charset.StandardCharsets;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class DocumentPipelineIT {

	@Autowired
	MockMvc mvc;

	@Autowired
	JobWorker worker;

	@Autowired
	JdbcClient jdbc;

	@Test
	void pdfIsParsedChunkedAndEmbeddedWithPageNumbers() throws Exception {
		var api = new ApiClient(mvc);
		var user = api.register();
		byte[] pdf = TestDocuments.pdf(List.of(List.of("Introduction to the family records."),
				List.of("The car insurance policy CI-7781 renews on 3 January 2027."),
				List.of("The water purifier warranty ends in August 2027.")));

		var upload = api.upload(user, "records.pdf", pdf);
		assertThat(upload.getResponse().getStatus()).isEqualTo(201);
		String id = JsonPath.read(upload.getResponse().getContentAsString(), "$.document.id");
		assertThat((String) JsonPath.read(upload.getResponse().getContentAsString(), "$.document.status"))
			.isEqualTo("UPLOADED");

		worker.drain();

		String doc = api.getJson(user, "/api/v1/documents/" + id);
		assertThat((String) JsonPath.read(doc, "$.status")).isEqualTo("READY");
		assertThat((Integer) JsonPath.read(doc, "$.pageCount")).isEqualTo(3);
		assertThat((Integer) JsonPath.read(doc, "$.chunkCount")).isGreaterThan(0);
		assertThat((String) JsonPath.read(doc, "$.mimeType")).isEqualTo("application/pdf");

		String chunks = api.getJson(user, "/api/v1/documents/" + id + "/chunks");
		assertThat((String) JsonPath.read(chunks, "$[0].content")).contains("CI-7781");

		long embeddingCalls = jdbc.sql("SELECT count(*) FROM llm_calls WHERE user_id = :u AND purpose = 'embed_document'")
			.param("u", user.id())
			.query(Long.class)
			.single();
		assertThat(embeddingCalls).isEqualTo(1);
	}

	@Test
	void signedFileLinkServesOriginalAndRejectsTampering() throws Exception {
		var api = new ApiClient(mvc);
		var user = api.register();
		byte[] text = "Original bytes".getBytes(StandardCharsets.UTF_8);
		String id = JsonPath.read(api.upload(user, "orig.txt", text).getResponse().getContentAsString(), "$.document.id");

		String url = JsonPath.read(api.getJson(user, "/api/v1/documents/" + id + "/file"), "$.url");
		assertThat(url).startsWith("/api/v1/files/");

		// No bearer token needed: the signed link is the credential, like an S3 presigned URL.
		var file = mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse();
		assertThat(file.getContentAsByteArray()).isEqualTo(text);
		assertThat(file.getHeader("Content-Disposition")).contains("inline").contains("orig.txt");

		String tampered = url.substring(0, url.length() - 3) + (url.endsWith("AAA") ? "BBB" : "AAA");
		mvc.perform(get(tampered)).andExpect(status().isNotFound());
	}

	@Test
	void duplicateUploadReturnsExistingDocument() throws Exception {
		var api = new ApiClient(mvc);
		var user = api.register();
		byte[] text = "Grocery list: rice, dal, turmeric.".getBytes(StandardCharsets.UTF_8);

		var first = api.upload(user, "list.txt", text);
		var second = api.upload(user, "list-copy.txt", text);

		assertThat(second.getResponse().getStatus()).isEqualTo(200);
		assertThat((Boolean) JsonPath.read(second.getResponse().getContentAsString(), "$.duplicate")).isTrue();
		assertThat((String) JsonPath.read(second.getResponse().getContentAsString(), "$.document.id"))
			.isEqualTo(JsonPath.read(first.getResponse().getContentAsString(), "$.document.id"));
	}

	@Test
	void corruptPdfEndsFailedWithReadableError() throws Exception {
		var api = new ApiClient(mvc);
		var user = api.register();

		var upload = api.upload(user, "broken.pdf", "%PDF-1.7\nthis is not really a pdf".getBytes());
		String id = JsonPath.read(upload.getResponse().getContentAsString(), "$.document.id");
		worker.drain();

		String doc = api.getJson(user, "/api/v1/documents/" + id);
		assertThat((String) JsonPath.read(doc, "$.status")).isEqualTo("FAILED");
		assertThat((String) JsonPath.read(doc, "$.error")).contains("PDF could not be read");
	}

	@Test
	void unsupportedTypeIsRejectedByContentNotExtension() throws Exception {
		var api = new ApiClient(mvc);
		var user = api.register();
		byte[] png = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0 };

		var upload = api.upload(user, "sneaky.txt", png);

		assertThat(upload.getResponse().getStatus()).isEqualTo(415);
	}

	@Test
	void deleteHidesDocumentAndPurgesIt() throws Exception {
		var api = new ApiClient(mvc);
		var user = api.register();
		var upload = api.upload(user, "note.md", "# Note\n\nRemember the milk.".getBytes(StandardCharsets.UTF_8));
		String id = JsonPath.read(upload.getResponse().getContentAsString(), "$.document.id");
		worker.drain();

		mvc.perform(api.authed(delete("/api/v1/documents/" + id), user)).andExpect(status().isNoContent());
		mvc.perform(api.authed(get("/api/v1/documents/" + id), user)).andExpect(status().isNotFound());
		worker.drain();

		long rows = jdbc.sql("SELECT count(*) FROM documents WHERE id = CAST(:id AS uuid)").param("id", id)
			.query(Long.class).single();
		assertThat(rows).isZero();
	}

	@Test
	void collectionsFilterDocuments() throws Exception {
		var api = new ApiClient(mvc);
		var user = api.register();
		String collection = api.postJson(user, "/api/v1/collections", "{\"name\":\"Insurance\"}")
			.getResponse().getContentAsString();
		String collectionId = JsonPath.read(collection, "$.id");

		mvc.perform(api.authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.multipart("/api/v1/documents")
			.file(new org.springframework.mock.web.MockMultipartFile("file", "a.txt", "text/plain", "policy".getBytes()))
			.param("collectionId", collectionId), user)).andExpect(status().isCreated());
		api.upload(user, "b.txt", "unrelated".getBytes());

		mvc.perform(api.authed(get("/api/v1/documents").param("collectionId", collectionId), user))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].filename").value("a.txt"));
	}

}
