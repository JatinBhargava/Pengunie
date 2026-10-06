package com.pengunie.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.chunking")
public record ChunkingProperties(int targetTokens, int overlapTokens) {
}
