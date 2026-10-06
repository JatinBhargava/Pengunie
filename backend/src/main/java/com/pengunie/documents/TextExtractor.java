package com.pengunie.documents;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import com.pengunie.jobs.PermanentJobFailure;

import org.springframework.stereotype.Component;

/**
 * Extracts text page by page so that chunks, and therefore citations, keep page provenance. PDFs go
 * through PDFBox; other formats through Tika and are treated as a single page.
 */
@Component
public class TextExtractor {

	private static final Pattern HYPHENATED_BREAK = Pattern.compile("(\\p{L})-\\n(\\p{Ll})");

	private static final Pattern INLINE_WHITESPACE = Pattern.compile("[\\t\\x0B\\f\\r ]+");

	private static final Pattern MANY_NEWLINES = Pattern.compile("\\n{3,}");

	public List<String> extractPages(byte[] content, String mimeType) {
		List<String> raw = "application/pdf".equals(mimeType) ? pdfPages(content) : List.of(tikaText(content));
		return raw.stream().map(TextExtractor::normalize).toList();
	}

	private List<String> pdfPages(byte[] content) {
		try (PDDocument pdf = Loader.loadPDF(content)) {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			List<String> pages = new ArrayList<>(pdf.getNumberOfPages());
			for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
				stripper.setStartPage(page);
				stripper.setEndPage(page);
				pages.add(stripper.getText(pdf));
			}
			return pages;
		}
		catch (InvalidPasswordException ex) {
			throw new PermanentJobFailure("PDF is password protected", ex);
		}
		catch (IOException ex) {
			throw new PermanentJobFailure("PDF could not be read: " + ex.getMessage(), ex);
		}
	}

	private String tikaText(byte[] content) {
		try {
			BodyContentHandler handler = new BodyContentHandler(-1);
			new AutoDetectParser().parse(new ByteArrayInputStream(content), handler, new Metadata(), new ParseContext());
			return handler.toString();
		}
		catch (IOException | SAXException | TikaException ex) {
			throw new PermanentJobFailure("Document could not be parsed: " + ex.getMessage(), ex);
		}
	}

	static String normalize(String text) {
		String result = text.replace("\r\n", "\n").replace(' ', ' ');
		result = HYPHENATED_BREAK.matcher(result).replaceAll("$1$2");
		result = INLINE_WHITESPACE.matcher(result).replaceAll(" ");
		result = result.lines().map(String::strip).reduce((a, b) -> a + "\n" + b).orElse("");
		result = MANY_NEWLINES.matcher(result).replaceAll("\n\n");
		return result.strip();
	}

}
