package com.pengunie.search;

import java.util.List;
import java.util.UUID;

import com.pengunie.common.AccessScope;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The only path from the index to application code. Every query requires an {@link AccessScope}
 * and filters on it in SQL (PRD §4: authorization before retrieval). Deleted and unfinished
 * documents are never returned.
 */
@Repository
public class ChunkSearchRepository {

	public record SearchFilter(UUID collectionId, UUID documentId) {

		public static final SearchFilter NONE = new SearchFilter(null, null);

	}

	public record ChunkHit(UUID chunkId, UUID documentId, String documentTitle, int pageStart, int pageEnd,
			String heading, String content, String snippet, double score) {
	}

	/** Marks keyword matches in snippets; private-use code points cannot clash with document text. */
	public static final String MATCH_START = "";

	public static final String MATCH_END = "";

	private static final String SCOPE_PREDICATE = """
			c.owner_user_id = :owner AND d.owner_user_id = :owner AND d.deleted_at IS NULL AND d.status = 'READY'
			AND (CAST(:collection AS uuid) IS NULL OR d.collection_id = :collection)
			AND (CAST(:document AS uuid) IS NULL OR c.document_id = :document)
			""";

	private final JdbcClient jdbc;

	ChunkSearchRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Cosine similarity (1 = identical) over the HNSW index. */
	public List<ChunkHit> semantic(AccessScope scope, float[] queryVector, SearchFilter filter, int topK,
			double minScore) {
		return jdbc.sql("""
				SELECT c.id AS chunk_id, c.document_id, d.title AS document_title, c.page_start, c.page_end, c.heading,
				       c.content, left(c.content, 320) AS snippet,
				       1 - (c.embedding <=> CAST(:query AS vector)) AS score
				FROM chunks c JOIN documents d ON d.id = c.document_id
				WHERE c.embedding IS NOT NULL AND """ + " " + SCOPE_PREDICATE + """
				ORDER BY c.embedding <=> CAST(:query AS vector)
				LIMIT :limit
				""")
			.param("query", EmbeddingService.toPgVector(queryVector))
			.param("owner", scope.userId())
			.param("collection", filter.collectionId())
			.param("document", filter.documentId())
			.param("limit", topK)
			.query(ChunkHit.class)
			.list()
			.stream()
			.filter(hit -> hit.score() >= minScore)
			.toList();
	}

	/** Postgres full-text search; good at exact identifiers and rare terms that embeddings blur. */
	public List<ChunkHit> keyword(AccessScope scope, String query, SearchFilter filter, int topK) {
		return jdbc.sql("""
				SELECT c.id AS chunk_id, c.document_id, d.title AS document_title, c.page_start, c.page_end, c.heading,
				       c.content,
				       ts_headline('english', c.content, q,
				                   'StartSel=' || :start || ', StopSel=' || :stop || ', MaxWords=45, MinWords=20, MaxFragments=2')
				           AS snippet,
				       ts_rank_cd(c.tsv, q) AS score
				FROM chunks c JOIN documents d ON d.id = c.document_id,
				     websearch_to_tsquery('english', :text) q
				WHERE c.tsv @@ q AND """ + " " + SCOPE_PREDICATE + """
				ORDER BY score DESC
				LIMIT :limit
				""")
			.param("text", query)
			.param("start", MATCH_START)
			.param("stop", MATCH_END)
			.param("owner", scope.userId())
			.param("collection", filter.collectionId())
			.param("document", filter.documentId())
			.param("limit", topK)
			.query(ChunkHit.class)
			.list();
	}

	public record ChunkPreview(UUID id, int ordinal, int pageStart, int pageEnd, String heading, String content,
			int tokenCount) {
	}

	public List<ChunkPreview> listForDocument(AccessScope scope, UUID documentId) {
		return jdbc.sql("""
				SELECT c.id, c.ordinal, c.page_start, c.page_end, c.heading, c.content, c.token_count
				FROM chunks c JOIN documents d ON d.id = c.document_id
				WHERE c.document_id = :document AND c.owner_user_id = :owner AND d.deleted_at IS NULL
				ORDER BY c.ordinal
				""")
			.param("document", documentId)
			.param("owner", scope.userId())
			.query(ChunkPreview.class)
			.list();
	}

}
