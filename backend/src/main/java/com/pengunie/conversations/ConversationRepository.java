package com.pengunie.conversations;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pengunie.common.AccessScope;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ConversationRepository {

	record ConversationView(UUID id, String title, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	record MessageRow(UUID id, String role, String content, String grounding, String citations,
			OffsetDateTime createdAt) {
	}

	private final JdbcClient jdbc;

	ConversationRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	UUID create(AccessScope scope, String title) {
		return jdbc.sql("INSERT INTO conversations (owner_user_id, title) VALUES (:owner, :title) RETURNING id")
			.param("owner", scope.userId())
			.param("title", title)
			.query(UUID.class)
			.single();
	}

	Optional<ConversationView> find(AccessScope scope, UUID id) {
		return jdbc.sql("""
				SELECT id, title, created_at, updated_at FROM conversations WHERE id = :id AND owner_user_id = :owner
				""")
			.param("id", id)
			.param("owner", scope.userId())
			.query(ConversationView.class)
			.optional();
	}

	List<ConversationView> list(AccessScope scope) {
		return jdbc.sql("""
				SELECT id, title, created_at, updated_at FROM conversations WHERE owner_user_id = :owner
				ORDER BY updated_at DESC LIMIT 100
				""")
			.param("owner", scope.userId())
			.query(ConversationView.class)
			.list();
	}

	boolean delete(AccessScope scope, UUID id) {
		return jdbc.sql("DELETE FROM conversations WHERE id = :id AND owner_user_id = :owner")
			.param("id", id)
			.param("owner", scope.userId())
			.update() > 0;
	}

	/** Callers must have verified ownership of the conversation via {@link #find}. */
	List<MessageRow> messages(UUID conversationId, int limit) {
		return jdbc.sql("""
				SELECT * FROM (
				    SELECT id, role, content, grounding, citations::text AS citations, created_at FROM messages
				    WHERE conversation_id = :id ORDER BY created_at DESC LIMIT :limit) recent
				ORDER BY created_at
				""")
			.param("id", conversationId)
			.param("limit", limit)
			.query(MessageRow.class)
			.list();
	}

	UUID addMessage(UUID conversationId, String role, String content, Grounding grounding, String citationsJson,
			String retrievalJson, UUID llmCallId) {
		UUID id = jdbc.sql("""
				INSERT INTO messages (conversation_id, role, content, grounding, citations, retrieval, llm_call_id)
				VALUES (:conversation, :role, :content, :grounding, CAST(:citations AS jsonb), CAST(:retrieval AS jsonb), :llm)
				RETURNING id
				""")
			.param("conversation", conversationId)
			.param("role", role)
			.param("content", content)
			.param("grounding", grounding == null ? null : grounding.name())
			.param("citations", citationsJson)
			.param("retrieval", retrievalJson)
			.param("llm", llmCallId)
			.query(UUID.class)
			.single();
		jdbc.sql("UPDATE conversations SET updated_at = now() WHERE id = :id").param("id", conversationId).update();
		return id;
	}

}
