package com.pengunie.storage;

import java.nio.file.Files;

import com.pengunie.common.ApiException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Serves files for signed links issued by {@link FileSystemObjectStorage}; the token is the credential. */
@RestController
@ConditionalOnBean(FileSystemObjectStorage.class)
class SignedFileController {

	private final FileSystemObjectStorage storage;

	SignedFileController(FileSystemObjectStorage storage) {
		this.storage = storage;
	}

	@GetMapping("/api/v1/files/{token}")
	ResponseEntity<Resource> download(@PathVariable String token) {
		var file = storage.verify(token)
			.orElseThrow(() -> ApiException.notFound("File link is invalid or expired; file"));
		var path = storage.resolve(file.key());
		if (!Files.exists(path)) {
			throw ApiException.notFound("File");
		}
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(file.contentType()))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.inline().filename(file.filename()).build().toString())
			.header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
			.header("X-Content-Type-Options", "nosniff")
			.body(new FileSystemResource(path));
	}

}
