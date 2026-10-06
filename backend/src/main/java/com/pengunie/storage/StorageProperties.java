package com.pengunie.storage;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param type {@code filesystem} (local dev, tests) or {@code s3} (AWS or any S3-compatible store)
 */
@ConfigurationProperties("app.storage")
public record StorageProperties(String type, Duration presignTtl, Filesystem filesystem, S3 s3) {

	public record Filesystem(String root, String urlSigningSecret) {
	}

	public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey,
			boolean pathStyle) {
	}

}
