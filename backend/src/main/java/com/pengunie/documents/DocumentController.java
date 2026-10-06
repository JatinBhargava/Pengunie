package com.pengunie.documents;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pengunie.common.AccessScope;
import com.pengunie.common.ApiException;
import com.pengunie.common.Requests;
import com.pengunie.storage.ObjectStorage;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
class DocumentController {

	private final DocumentService service;

	private final DocumentRepository documents;

	private final ObjectStorage storage;

	DocumentController(DocumentService service, DocumentRepository documents, ObjectStorage storage) {
		this.service = service;
		this.documents = documents;
		this.storage = storage;
	}

	record UpdateDocumentRequest(UUID collectionId) {
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<DocumentService.UploadResult> upload(AccessScope scope, @RequestPart("file") MultipartFile file,
			@RequestParam(required = false) String title, @RequestParam(required = false) UUID collectionId,
			HttpServletRequest http) {
		var result = service.upload(scope, file, title, collectionId, Requests.clientIp(http));
		return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
	}

	@GetMapping
	List<DocumentView> list(AccessScope scope, @RequestParam(required = false) UUID collectionId) {
		return documents.list(scope, collectionId);
	}

	@GetMapping("/{id}")
	DocumentView get(AccessScope scope, @PathVariable UUID id) {
		return documents.find(scope, id).orElseThrow(() -> ApiException.notFound("Document"));
	}

	@GetMapping("/{id}/file")
	Map<String, String> file(AccessScope scope, @PathVariable UUID id) {
		var doc = documents.findStored(scope, id).orElseThrow(() -> ApiException.notFound("Document"));
		return Map.of("url", storage.presignedGet(doc.storageKey(), doc.filename(), doc.mimeType()).toString());
	}

	@PatchMapping("/{id}")
	DocumentView update(AccessScope scope, @PathVariable UUID id, @RequestBody UpdateDocumentRequest request) {
		service.moveToCollection(scope, id, request.collectionId());
		return get(scope, id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(AccessScope scope, @PathVariable UUID id, HttpServletRequest http) {
		service.delete(scope, id, Requests.clientIp(http));
	}

}
