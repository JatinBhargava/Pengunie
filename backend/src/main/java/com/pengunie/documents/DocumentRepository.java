package com.pengunie.documents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pengunie.common.AccessScope;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Document persistence. Methods that serve user requests take an {@link AccessScope}; the
 * unscoped {@code *ForProcessing} methods are for background jobs acting on a known document id.
 */
@Repository
public class DocumentRepository {

	private static final String VIEW_COLUMNS = """
			d.id, d.collection_id, d.title, d.filename, d.mime_type, d.size_bytes, d.status, d.page_count,
			(SELECT count(*) FROM chunks c WHERE c.document_id = d.id) AS chunk_count, d.error, d.created_at, d.updated_at
			""";

	public record StoredDocument(UUID id, UUID ownerUserId, String title, String filename, String mimeType,
			String storageKey, DocumentStatus status) {
	}

	private final JdbcClient jdbc;

	DocumentRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void insert(UUID id, AccessScope scope, UUID collectionId, String title, String filename, String mimeType,
			long size, String sha256, String storageKey) {
		jdbc.sql("""
				INSERT INTO documents (id, owner_user_id, collection_id, title, filename, mime_type, size_bytes, sha256, storage_key)
				VALUES (:id, :owner, :collection, :title, :filename, :mime, :size, :sha, :key)
				""")
			.param("id", id)
			.param("owner", scope.userId())
			.param("collection", collectionId)
			.param("title", title)
			.param("filename", filename)
			.param("mime", mimeType)
			.param("size", size)
			.param("sha", sha256)
			.param("key", storageKey)
			.update();
	}

	public Optional<UUID> findIdBySha(AccessScope scope, String sha256) {
		return jdbc.sql("SELECT id FROM documents WHERE owner_user_id = :owner AND sha256 = :sha AND deleted_at IS NULL")
			.param("owner", scope.userId())
			.param("sha", sha256)
			.query(UUID.class)
			.optional();
	}

	public Optional<DocumentView> find(AccessScope scope, UUID id) {
		return jdbc.sql("SELECT " + VIEW_COLUMNS
				+ " FROM documents d WHERE d.id = :id AND d.owner_user_id = :owner AND d.deleted_at IS NULL")
			.param("id", id)
			.param("owner", scope.userId())
			.query(DocumentView.class)
			.optional();
	}

	public Optional<StoredDocument> findStored(AccessScope scope, UUID id) {
		return jdbc.sql("""
				SELECT id, owner_user_id, title, filename, mime_type, storage_key, status FROM documents
				WHERE id = :id AND owner_user_id = :owner AND deleted_at IS NULL
				""")
			.param("id", id)
			.param("owner", scope.userId())
			.query(StoredDocument.class)
			.optional();
	}

	public List<DocumentView> list(AccessScope scope, UUID collectionId) {
		return jdbc.sql("SELECT " + VIEW_COLUMNS + """
				FROM documents d
				WHERE d.owner_user_id = :owner AND d.deleted_at IS NULL
				  AND (CAST(:collection AS uuid) IS NULL OR d.collection_id = :collection)
				ORDER BY d.created_at DESC
				""")
			.param("owner", scope.userId())
			.param("collection", collectionId)
			.query(DocumentView.class)
			.list();
	}

	public boolean softDelete(AccessScope scope, UUID id) {
		return jdbc.sql("""
				UPDATE documents SET deleted_at = now(), updated_at = now()
				WHERE id = :id AND owner_user_id = :owner AND deleted_at IS NULL
				""")
			.param("id", id)
			.param("owner", scope.userId())
			.update() > 0;
	}

	public boolean moveToCollection(AccessScope scope, UUID id, UUID collectionId) {
		return jdbc.sql("""
				UPDATE documents SET collection_id = :collection, updated_at = now()
				WHERE id = :id AND owner_user_id = :owner AND deleted_at IS NULL
				""")
			.param("id", id)
			.param("owner", scope.userId())
			.param("collection", collectionId)
			.update() > 0;
	}

	// --- background processing (no request scope) ---

	public Optional<StoredDocument> findForProcessing(UUID id) {
		return jdbc.sql("""
				SELECT id, owner_user_id, title, filename, mime_type, storage_key, status FROM documents
				WHERE id = :id AND deleted_at IS NULL
				""")
			.param("id", id)
			.query(StoredDocument.class)
			.optional();
	}

	public Optional<String> findStorageKeyIncludingDeleted(UUID id) {
		return jdbc.sql("SELECT storage_key FROM documents WHERE id = :id").param("id", id).query(String.class).optional();
	}

	public void updateStatus(UUID id, DocumentStatus status, String error) {
		jdbc.sql("UPDATE documents SET status = :status, error = :error, updated_at = now() WHERE id = :id")
			.param("id", id)
			.param("status", status.name())
			.param("error", error)
			.update();
	}

	public void replacePages(UUID id, List<String> pages) {
		jdbc.sql("DELETE FROM document_pages WHERE document_id = :id").param("id", id).update();
		for (int i = 0; i < pages.size(); i++) {
			jdbc.sql("INSERT INTO document_pages (document_id, page_number, content) VALUES (:id, :page, :content)")
				.param("id", id)
				.param("page", i + 1)
				.param("content", pages.get(i))
				.update();
		}
		jdbc.sql("UPDATE documents SET page_count = :count, updated_at = now() WHERE id = :id")
			.param("id", id)
			.param("count", pages.size())
			.update();
	}

	public List<String> pages(UUID id) {
		return jdbc.sql("SELECT content FROM document_pages WHERE document_id = :id ORDER BY page_number")
			.param("id", id)
			.query(String.class)
			.list();
	}

	/** Page texts for an inclusive page range, keyed by page number. */
	public java.util.Map<Integer, String> pages(UUID id, int fromPage, int toPage) {
		java.util.Map<Integer, String> pages = new java.util.LinkedHashMap<>();
		jdbc.sql("""
				SELECT page_number, content FROM document_pages
				WHERE document_id = :id AND page_number BETWEEN :from AND :to ORDER BY page_number
				""")
			.param("id", id)
			.param("from", fromPage)
			.param("to", toPage)
			.query((rs, n) -> pages.put(rs.getInt(1), rs.getString(2)))
			.list();
		return pages;
	}

	public void hardDelete(UUID id) {
		jdbc.sql("DELETE FROM documents WHERE id = :id").param("id", id).update();
	}

}
