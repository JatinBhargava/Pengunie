package com.pengunie.documents;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.pengunie.audit.AuditService;
import com.pengunie.common.AccessScope;
import com.pengunie.common.ApiException;
import com.pengunie.common.Hashing;
import com.pengunie.jobs.JobQueue;
import com.pengunie.storage.ObjectStorage;
import org.apache.tika.Tika;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

	public static final String PARSE_JOB = "PARSE_DOCUMENT";

	public static final String PURGE_JOB = "PURGE_DOCUMENT";

	public record UploadResult(DocumentView document, boolean duplicate) {
	}

	private final DocumentRepository documents;

	private final ObjectStorage storage;

	private final JobQueue jobs;

	private final AuditService audit;

	private final DocumentProperties props;

	private final TransactionTemplate tx;

	private final JdbcClient jdbc;

	private final Tika tika = new Tika();

	DocumentService(DocumentRepository documents, ObjectStorage storage, JobQueue jobs, AuditService audit,
			DocumentProperties props, TransactionTemplate tx, JdbcClient jdbc) {
		this.documents = documents;
		this.storage = storage;
		this.jobs = jobs;
		this.audit = audit;
		this.props = props;
		this.tx = tx;
		this.jdbc = jdbc;
	}

	public UploadResult upload(AccessScope scope, MultipartFile file, String title, UUID collectionId, String ip) {
		if (file.isEmpty()) {
			throw ApiException.badRequest("empty_file", "File is empty");
		}
		byte[] content;
		try {
			content = file.getBytes();
		}
		catch (IOException ex) {
			throw ApiException.badRequest("unreadable_file", "Could not read upload");
		}
		String filename = sanitizeFilename(file.getOriginalFilename());
		// Trust the bytes, not the client-supplied Content-Type.
		String mimeType = tika.detect(content, filename);
		if (!props.allowedMimeTypes().contains(mimeType)) {
			throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_type",
					"Unsupported file type " + mimeType + ". Supported: PDF, TXT, Markdown, DOCX.");
		}
		CollectionController.requireOwned(jdbc, scope, collectionId);

		String sha256 = Hashing.sha256Hex(content);
		Optional<UUID> existing = documents.findIdBySha(scope, sha256);
		if (existing.isPresent()) {
			return new UploadResult(documents.find(scope, existing.get()).orElseThrow(), true);
		}

		UUID id = UUID.randomUUID();
		String storageKey = "users/" + scope.userId() + "/docs/" + id;
		String effectiveTitle = title == null || title.isBlank() ? stripExtension(filename) : title.strip();
		storage.put(storageKey, content, mimeType);
		try {
			tx.executeWithoutResult(status -> {
				documents.insert(id, scope, collectionId, effectiveTitle, filename, mimeType, content.length, sha256,
						storageKey);
				jobs.enqueue(PARSE_JOB, Map.of("documentId", id.toString()), "parse:" + id);
				audit.record(scope.userId(), "DOCUMENT_UPLOADED", "document", id,
						Map.of("mimeType", mimeType, "sizeBytes", content.length), ip);
			});
		}
		catch (DuplicateKeyException ex) {
			// Concurrent upload of the same file won the race.
			storage.delete(storageKey);
			UUID winner = documents.findIdBySha(scope, sha256).orElseThrow(() -> ex);
			return new UploadResult(documents.find(scope, winner).orElseThrow(), true);
		}
		return new UploadResult(documents.find(scope, id).orElseThrow(), false);
	}

	public void delete(AccessScope scope, UUID id, String ip) {
		tx.executeWithoutResult(status -> {
			if (!documents.softDelete(scope, id)) {
				throw ApiException.notFound("Document");
			}
			jobs.enqueue(PURGE_JOB, Map.of("documentId", id.toString()), "purge:" + id);
			audit.record(scope.userId(), "DOCUMENT_DELETED", "document", id, Map.of(), ip);
		});
	}

	public void moveToCollection(AccessScope scope, UUID id, UUID collectionId) {
		CollectionController.requireOwned(jdbc, scope, collectionId);
		if (!documents.moveToCollection(scope, id, collectionId)) {
			throw ApiException.notFound("Document");
		}
	}

	static String sanitizeFilename(String original) {
		String name = original == null ? "upload" : original.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").strip();
		if (name.isEmpty()) {
			name = "upload";
		}
		return name.length() > 300 ? name.substring(name.length() - 300) : name;
	}

	private static String stripExtension(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot > 0 ? filename.substring(0, dot) : filename;
	}

}
