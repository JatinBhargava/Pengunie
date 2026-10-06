package com.pengunie.documents;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pengunie.common.Hashing;
import com.pengunie.jobs.JobHandler;
import com.pengunie.jobs.JobQueue;
import com.pengunie.jobs.PermanentJobFailure;
import com.pengunie.storage.ObjectStorage;
import tools.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Pipeline stage 1 (PRD §15.2): object storage → page text → document_pages, then queue indexing. */
@Component
class ParseDocumentHandler implements JobHandler {

	static final String INDEX_JOB = "INDEX_DOCUMENT";

	private final DocumentRepository documents;

	private final ObjectStorage storage;

	private final TextExtractor extractor;

	private final JobQueue jobs;

	private final TransactionTemplate tx;

	ParseDocumentHandler(DocumentRepository documents, ObjectStorage storage, TextExtractor extractor, JobQueue jobs,
			TransactionTemplate tx) {
		this.documents = documents;
		this.storage = storage;
		this.extractor = extractor;
		this.jobs = jobs;
		this.tx = tx;
	}

	@Override
	public String type() {
		return DocumentService.PARSE_JOB;
	}

	@Override
	public void handle(JsonNode payload) throws Exception {
		UUID id = UUID.fromString(payload.get("documentId").asString());
		var doc = documents.findForProcessing(id).orElse(null);
		if (doc == null) {
			return; // deleted before processing
		}
		documents.updateStatus(id, DocumentStatus.PARSING, null);
		byte[] content;
		try (InputStream in = storage.get(doc.storageKey())) {
			content = in.readAllBytes();
		}
		List<String> pages = extractor.extractPages(content, doc.mimeType());
		if (pages.stream().allMatch(String::isBlank)) {
			throw new PermanentJobFailure(
					"No extractable text found. Scanned documents need OCR, which is not supported yet.");
		}
		String contentHash = Hashing.sha256Hex(String.join("\f", pages).getBytes(StandardCharsets.UTF_8));
		tx.executeWithoutResult(status -> {
			documents.replacePages(id, pages);
			documents.updateStatus(id, DocumentStatus.EMBEDDING, null);
			jobs.enqueue(INDEX_JOB, Map.of("documentId", id.toString()), "index:" + id + ":" + contentHash);
		});
	}

	@Override
	public void onGiveUp(JsonNode payload, Exception error) {
		UUID id = UUID.fromString(payload.get("documentId").asString());
		documents.updateStatus(id, DocumentStatus.FAILED, error.getMessage());
	}

}
