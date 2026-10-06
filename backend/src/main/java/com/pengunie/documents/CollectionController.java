package com.pengunie.documents;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.pengunie.common.AccessScope;
import com.pengunie.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/collections")
class CollectionController {

	private final JdbcClient jdbc;

	CollectionController(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	record CollectionView(UUID id, String name, long documentCount, OffsetDateTime createdAt) {
	}

	record CreateCollectionRequest(@NotBlank @Size(max = 120) String name) {
	}

	@GetMapping
	List<CollectionView> list(AccessScope scope) {
		return jdbc.sql("""
				SELECT c.id, c.name, c.created_at,
				       (SELECT count(*) FROM documents d WHERE d.collection_id = c.id AND d.deleted_at IS NULL) AS document_count
				FROM collections c WHERE c.owner_user_id = :owner ORDER BY lower(c.name)
				""")
			.param("owner", scope.userId())
			.query(CollectionView.class)
			.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CollectionView create(AccessScope scope, @Valid @RequestBody CreateCollectionRequest request) {
		try {
			return jdbc.sql("""
					INSERT INTO collections (owner_user_id, name) VALUES (:owner, :name)
					RETURNING id, name, created_at, 0 AS document_count
					""")
				.param("owner", scope.userId())
				.param("name", request.name().strip())
				.query(CollectionView.class)
				.single();
		}
		catch (DuplicateKeyException ex) {
			throw ApiException.conflict("collection_exists", "A collection with this name already exists");
		}
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(AccessScope scope, @PathVariable UUID id) {
		int deleted = jdbc.sql("DELETE FROM collections WHERE id = :id AND owner_user_id = :owner")
			.param("id", id)
			.param("owner", scope.userId())
			.update();
		if (deleted == 0) {
			throw ApiException.notFound("Collection");
		}
	}

	static void requireOwned(JdbcClient jdbc, AccessScope scope, UUID collectionId) {
		if (collectionId == null) {
			return;
		}
		long count = jdbc.sql("SELECT count(*) FROM collections WHERE id = :id AND owner_user_id = :owner")
			.param("id", collectionId)
			.param("owner", scope.userId())
			.query(Long.class)
			.single();
		if (count == 0) {
			throw ApiException.notFound("Collection");
		}
	}

}
