package com.vitassistant.service;

import com.vitassistant.config.RagProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfLoaderServiceTest {

    @Test
    void extractsTextFromPdfUpload() throws Exception {
        ByteArrayOutputStream pdfBytes = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("VIT semester dates are listed in this searchable PDF.");
                content.endText();
            }
            document.save(pdfBytes);
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "calendar.pdf",
                "application/pdf",
                pdfBytes.toByteArray()
        );
        String extracted = new PdfLoaderService(new RagProperties()).extractText(file);

        assertTrue(extracted.contains("VIT semester dates"));
    }
}
