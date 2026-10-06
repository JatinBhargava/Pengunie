package com.pengunie.search;

import java.util.List;
import java.util.UUID;

import com.pengunie.documents.DocumentRepository;
import com.pengunie.documents.DocumentStatus;
import com.pengunie.jobs.JobHandler;
import com.pengunie.jobs.PermanentJobFailure;
import tools.jackson.databind.JsonNode;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pipeline stage 2: page text → chunks → embeddings → index. Idempotent: the document's chunks are
 * replaced atomically, so a retry or a re-index never leaves duplicates or a half-built index.
 */
@Component
class IndexDocumentHandler implements JobHandler {

	private final DocumentRepository documents;

	private final Chunker chunker;

	private final EmbeddingService embeddings;

	private final JdbcClient jdbc;

	private final TransactionTemplate tx;

	IndexDocumentHandler(DocumentRepository documents, Chunker chunker, EmbeddingService embeddings, JdbcClient jdbc,
			TransactionTemplate tx) {
		this.documents = documents;
		this.chunker = chunker;
		this.embeddings = embeddings;
		this.jdbc = jdbc;
		this.tx = tx;
	}

	@Override
	public String type() {
		return "INDEX_DOCUMENT";
	}

	@Override
	public void handle(JsonNode payload) {
		UUID id = UUID.fromString(payload.get("documentId").asString());
		var doc = documents.findForProcessing(id).orElse(null);
		if (doc == null) {
			return;
		}
		List<Chunker.Chunk> chunks = chunker.chunk(documents.pages(id));
		if (chunks.isEmpty()) {
			throw new PermanentJobFailure("Document produced no text chunks");
		}
		// Title and heading give each chunk context it would otherwise lose when read in isolation.
		List<String> texts = chunks.stream()
			.map(c -> doc.title() + (c.heading() == null ? "" : " — " + c.heading()) + "\n\n" + c.content())
			.toList();
		EmbeddingService.Embedded embedded = embeddings.embedAll(texts, doc.ownerUserId(), "embed_document", id);

		tx.executeWithoutResult(status -> {
			jdbc.sql("DELETE FROM chunks WHERE document_id = :id").param("id", id).update();
			for (int i = 0; i < chunks.size(); i++) {
				Chunker.Chunk chunk = chunks.get(i);
				jdbc.sql("""
						INSERT INTO chunks (document_id, owner_user_id, ordinal, page_start, page_end, heading, content,
						                    token_count, embedding, embedding_model)
						VALUES (:doc, :owner, :ordinal, :pageStart, :pageEnd, :heading, :content, :tokens,
						        CAST(:embedding AS vector), :model)
						""")
					.param("doc", id)
					.param("owner", doc.ownerUserId())
					.param("ordinal", chunk.ordinal())
					.param("pageStart", chunk.pageStart())
					.param("pageEnd", chunk.pageEnd())
					.param("heading", chunk.heading())
					.param("content", chunk.content())
					.param("tokens", chunk.tokenCount())
					.param("embedding", EmbeddingService.toPgVector(embedded.vectors().get(i)))
					.param("model", embedded.model())
					.update();
			}
			documents.updateStatus(id, DocumentStatus.READY, null);
		});
	}

	@Override
	public void onGiveUp(JsonNode payload, Exception error) {
		documents.updateStatus(UUID.fromString(payload.get("documentId").asString()), DocumentStatus.FAILED,
				"Indexing failed: " + error.getMessage());
	}

}
