package com.pengunie.documents;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextExtractorTest {

	@Test
	void normalizesWhitespaceAndHyphenation() {
		String text = "Distri-\nbuted   systems\r\n\r\n\r\n\r\nNext\tparagraph ";

		assertThat(TextExtractor.normalize(text)).isEqualTo("Distributed systems\n\nNext paragraph");
	}

	@Test
	void extractsPlainTextAsSinglePage() {
		var pages = new TextExtractor().extractPages("Hello world".getBytes(StandardCharsets.UTF_8), "text/plain");

		assertThat(pages).containsExactly("Hello world");
	}

	@Test
	void sanitizesFilenames() {
		assertThat(DocumentService.sanitizeFilename("../../etc/pass\"wd.pdf")).isEqualTo("passwd.pdf");
		assertThat(DocumentService.sanitizeFilename("C:\\Users\\me\\report.pdf")).isEqualTo("report.pdf");
		assertThat(DocumentService.sanitizeFilename(null)).isEqualTo("upload");
	}

}
