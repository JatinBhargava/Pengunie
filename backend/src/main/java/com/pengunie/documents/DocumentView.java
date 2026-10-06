package com.pengunie.documents;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentView(UUID id, UUID collectionId, String title, String filename, String mimeType,
		long sizeBytes, DocumentStatus status, Integer pageCount, Integer chunkCount, String error,
		OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
