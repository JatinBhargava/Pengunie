package com.pengunie.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public final class TestDocuments {

	private TestDocuments() {
	}

	/** Builds a PDF with one page per entry; each entry is a list of lines. */
	public static byte[] pdf(List<List<String>> pages) {
		try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			for (List<String> lines : pages) {
				PDPage page = new PDPage();
				doc.addPage(page);
				try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
					content.beginText();
					content.setFont(font, 11);
					content.setLeading(16);
					content.newLineAtOffset(50, 720);
					for (String line : lines) {
						content.showText(line);
						content.newLine();
					}
					content.endText();
				}
			}
			doc.save(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
