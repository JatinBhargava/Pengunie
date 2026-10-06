package com.pengunie.conversations;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.rag")
public record RagProperties(int topK, double minScore, int historyMessages) {
}
