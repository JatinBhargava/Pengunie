package com.pengunie.documents;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.documents")
public record DocumentProperties(Set<String> allowedMimeTypes) {
}
