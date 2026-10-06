package com.pengunie.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Stores objects on local disk for development and tests. "Presigned" links are HMAC-signed,
 * expiring tokens served by {@link SignedFileController}, mirroring S3 presigned URL semantics so
 * the rest of the app does not care which backend is active.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "filesystem", matchIfMissing = true)
class FileSystemObjectStorage implements ObjectStorage {

	private static final Logger log = LoggerFactory.getLogger(FileSystemObjectStorage.class);

	record SignedFile(String key, String filename, String contentType, long expiresAt) {
	}

	private final Path root;

	private final StorageProperties props;

	private final ObjectMapper json;

	private final byte[] secret;

	private final Clock clock = Clock.systemUTC();

	FileSystemObjectStorage(StorageProperties props, ObjectMapper json) throws IOException {
		this.props = props;
		this.json = json;
		this.root = Path.of(props.filesystem().root()).toAbsolutePath().normalize();
		Files.createDirectories(root);
		String configured = props.filesystem().urlSigningSecret();
		if (StringUtils.hasText(configured)) {
			this.secret = configured.getBytes(StandardCharsets.UTF_8);
		}
		else {
			this.secret = new byte[32];
			new SecureRandom().nextBytes(secret);
		}
		log.info("Storing documents on the local filesystem at {}", root);
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		Path target = resolve(key);
		try {
			Files.createDirectories(target.getParent());
			Path temp = Files.createTempFile(target.getParent(), ".upload", ".tmp");
			Files.write(temp, content);
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@Override
	public InputStream get(String key) {
		try {
			return Files.newInputStream(resolve(key));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@Override
	public URI presignedGet(String key, String downloadFilename, String contentType) {
		long expiresAt = clock.millis() + props.presignTtl().toMillis();
		String payload = base64(json.writeValueAsBytes(new SignedFile(key, downloadFilename, contentType, expiresAt)));
		return URI.create("/api/v1/files/" + payload + "." + base64(hmac(payload)));
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Returns the file described by a valid, unexpired token. */
	Optional<SignedFile> verify(String token) {
		int dot = token.indexOf('.');
		if (dot <= 0) {
			return Optional.empty();
		}
		String payload = token.substring(0, dot);
		byte[] signature;
		try {
			signature = Base64.getUrlDecoder().decode(token.substring(dot + 1));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
		if (!MessageDigest.isEqual(signature, hmac(payload))) {
			return Optional.empty();
		}
		SignedFile file = json.readValue(Base64.getUrlDecoder().decode(payload), SignedFile.class);
		return file.expiresAt() < clock.millis() ? Optional.empty() : Optional.of(file);
	}

	Path resolve(String key) {
		Path path = root.resolve(key).normalize();
		if (!path.startsWith(root)) {
			throw new IllegalArgumentException("Invalid storage key");
		}
		return path;
	}

	private byte[] hmac(String payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String base64(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
