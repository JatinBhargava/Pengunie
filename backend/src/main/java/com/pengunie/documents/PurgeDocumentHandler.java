package com.pengunie.documents;

import java.util.UUID;

import com.pengunie.jobs.JobHandler;
import com.pengunie.storage.ObjectStorage;
import tools.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;

/** Removes a soft-deleted document's file, pages and chunks. */
@Component
class PurgeDocumentHandler implements JobHandler {

	private final DocumentRepository documents;

	private final ObjectStorage storage;

	PurgeDocumentHandler(DocumentRepository documents, ObjectStorage storage) {
		this.documents = documents;
		this.storage = storage;
	}

	@Override
	public String type() {
		return DocumentService.PURGE_JOB;
	}

	@Override
	public void handle(JsonNode payload) {
		UUID id = UUID.fromString(payload.get("documentId").asString());
		documents.findStorageKeyIncludingDeleted(id).ifPresent(key -> {
			storage.delete(key);
			documents.hardDelete(id);
		});
	}

}
