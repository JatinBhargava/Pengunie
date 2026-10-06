package com.pengunie.storage;

import java.io.InputStream;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
class S3ObjectStorage implements ObjectStorage {

	private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

	private final S3Client s3;

	private final S3Presigner presigner;

	private final StorageProperties.S3 props;

	private final StorageProperties storage;

	S3ObjectStorage(StorageProperties storage) {
		this.storage = storage;
		this.props = storage.s3();
		// Static keys for S3-compatible stores; on AWS leave them empty to use the default chain (IAM role).
		AwsCredentialsProvider credentials = StringUtils.hasText(props.accessKey())
				? StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()))
				: DefaultCredentialsProvider.builder().build();
		var s3Config = S3Configuration.builder().pathStyleAccessEnabled(props.pathStyle()).build();
		var s3Builder = S3Client.builder()
			.region(Region.of(props.region()))
			.credentialsProvider(credentials)
			.serviceConfiguration(s3Config)
			.httpClientBuilder(UrlConnectionHttpClient.builder());
		var presignerBuilder = S3Presigner.builder()
			.region(Region.of(props.region()))
			.credentialsProvider(credentials)
			.serviceConfiguration(s3Config);
		if (StringUtils.hasText(props.endpoint())) {
			s3Builder.endpointOverride(URI.create(props.endpoint()));
			presignerBuilder.endpointOverride(URI.create(props.endpoint()));
		}
		this.s3 = s3Builder.build();
		this.presigner = presignerBuilder.build();
	}

	@EventListener(ApplicationReadyEvent.class)
	void ensureBucket() {
		try {
			s3.headBucket(b -> b.bucket(props.bucket()));
		}
		catch (NoSuchBucketException ex) {
			log.info("Creating bucket {}", props.bucket());
			s3.createBucket(b -> b.bucket(props.bucket()));
		}
		catch (Exception ex) {
			log.warn("Could not verify bucket {}: {}", props.bucket(), ex.getMessage());
		}
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		s3.putObject(b -> b.bucket(props.bucket()).key(key).contentType(contentType), RequestBody.fromBytes(content));
	}

	@Override
	public InputStream get(String key) {
		return s3.getObject(b -> b.bucket(props.bucket()).key(key));
	}

	@Override
	public URI presignedGet(String key, String downloadFilename, String contentType) {
		String disposition = "inline; filename=\"" + downloadFilename.replace("\"", "") + "\"";
		return URI.create(presigner
			.presignGetObject(p -> p.signatureDuration(storage.presignTtl())
				.getObjectRequest(g -> g.bucket(props.bucket())
					.key(key)
					.responseContentType(contentType)
					.responseContentDisposition(disposition)))
			.url()
			.toString());
	}

	@Override
	public void delete(String key) {
		s3.deleteObject(b -> b.bucket(props.bucket()).key(key));
	}

}
