package com.pengunie.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.embedding")
public record EmbeddingProperties(int dimensions, int batchSize) {
}
