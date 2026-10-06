package com.pengunie.support;

import java.io.IOException;
import java.nio.file.Files;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer(
				DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
	}

	@Bean
	DynamicPropertyRegistrar storageProperties() throws IOException {
		var root = Files.createTempDirectory("pios-storage");
		return registry -> {
			registry.add("app.storage.type", () -> "filesystem");
			registry.add("app.storage.filesystem.root", root::toString);
		};
	}

}
