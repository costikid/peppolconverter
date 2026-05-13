package com.bromleywebworks.peppol;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PdfTextExtractionTest {

    private static final String SAMPLE_PDF =
            "/Users/costic/Documents/GitHub/mini peppol converter tool/***REMOVED***";

    @Test
    @EnabledIfSystemProperty(named = "runLocalPdfTest", matches = "true")
    void extractText_fromSampleInvoice_returnsNonEmptyText() throws IOException {
        File pdfFile = new File(SAMPLE_PDF);
        assertTrue(pdfFile.exists(), "Sample PDF must exist at: " + SAMPLE_PDF);

        try (PDDocument document = PDDocument.load(pdfFile,
                MemoryUsageSetting.setupMixed(20 * 1024 * 1024L))) {
            assertTrue(document.getNumberOfPages() > 0, "PDF should have at least one page");

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            assertNotNull(text, "Extracted text must not be null");
            assertFalse(text.isBlank(), "Extracted text must not be blank");
            assertTrue(text.contains("INVOICE"), "Text should contain 'INVOICE'");
        }
    }
}
