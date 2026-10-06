package com.pengunie.audit;

import java.time.OffsetDateTime;
import java.util.List;

import com.pengunie.common.AccessScope;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit/events")
class AuditController {

	private final JdbcClient jdbc;

	AuditController(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	record AuditEventView(long id, String action, String resourceType, String resourceId, String metadata,
			OffsetDateTime createdAt) {
	}

	@GetMapping
	List<AuditEventView> list(AccessScope scope, @RequestParam(defaultValue = "50") int limit) {
		return jdbc.sql("""
				SELECT id, action, resource_type, resource_id, metadata::text AS metadata, created_at
				FROM audit_events WHERE actor_user_id = :uid ORDER BY created_at DESC LIMIT :limit
				""")
			.param("uid", scope.userId())
			.param("limit", Math.clamp(limit, 1, 200))
			.query(AuditEventView.class)
			.list();
	}

}
