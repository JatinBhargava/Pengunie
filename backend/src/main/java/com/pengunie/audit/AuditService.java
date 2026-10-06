package com.pengunie.audit;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Append-only trail of security-relevant and sensitive actions (PRD §20). */
@Service
public class AuditService {

	private final JdbcClient jdbc;

	private final ObjectMapper json;

	AuditService(JdbcClient jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	public void record(UUID actorUserId, String action, String resourceType, Object resourceId,
			Map<String, ?> metadata, String ip) {
		jdbc.sql("""
				INSERT INTO audit_events (actor_user_id, action, resource_type, resource_id, metadata, ip)
				VALUES (:actor, :action, :type, :rid, CAST(:metadata AS jsonb), :ip)
				""")
			.param("actor", actorUserId)
			.param("action", action)
			.param("type", resourceType)
			.param("rid", resourceId == null ? null : resourceId.toString())
			.param("metadata", json.writeValueAsString(metadata == null ? Map.of() : metadata))
			.param("ip", ip)
			.update();
	}

	public void record(UUID actorUserId, String action, String resourceType, Object resourceId) {
		record(actorUserId, action, resourceType, resourceId, Map.of(), null);
	}

}
