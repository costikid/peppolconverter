package com.bromleywebworks.peppol.service;

import com.bromleywebworks.peppol.dto.ConverterType;
import com.bromleywebworks.peppol.dto.UploadForm;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class FileUploadValidator {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_PAGE_COUNT = 10;
    private static final byte[] PDF_MAGIC_BYTES = { 0x25, 0x50, 0x44, 0x46 }; // %PDF
    private static final long PDF_MAX_MAIN_MEMORY = 20 * 1024 * 1024L; // 20MB cap for PDFBox

    public List<String> validate(UploadForm uploadForm, ConverterType converterType) {
        List<String> errors = new ArrayList<>();

        MultipartFile file = uploadForm.getPdfFile();
        if (file == null || file.isEmpty()) {
            errors.add("Please select a PDF file to upload");
            return errors;
        }

        validateBasicChecks(file, errors);
        if (!errors.isEmpty()) {
            return errors;
        }

        try (PDDocument document = PDDocument.load(file.getInputStream(),
                MemoryUsageSetting.setupMixed(PDF_MAX_MAIN_MEMORY))) {
            if (document.getNumberOfPages() > MAX_PAGE_COUNT) {
                errors.add("PDF exceeds maximum page limit (10 pages)");
                return errors;
            }
            if (converterType == ConverterType.FREEAGENT) {
                validateFreeAgentFormat(document, errors);
            } else if (converterType == ConverterType.QUICKBOOKS) {
                validateQuickBooksFormat(document, errors);
            }
        } catch (IOException e) {
            errors.add("The PDF file is corrupted or unreadable");
        }

        return errors;
    }

    /**
     * Validates a raw MultipartFile upload for use by the API endpoint.
     */
    public List<String> validate(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        if (file == null || file.isEmpty()) {
            errors.add("Please provide a PDF file");
            return errors;
        }
        validateBasicChecks(file, errors);
        if (!errors.isEmpty()) {
            return errors;
        }
        try (PDDocument document = PDDocument.load(file.getInputStream(),
                MemoryUsageSetting.setupMixed(PDF_MAX_MAIN_MEMORY))) {
            if (document.getNumberOfPages() > MAX_PAGE_COUNT) {
                errors.add("PDF exceeds maximum page limit (10 pages)");
            }
        } catch (IOException e) {
            errors.add("The PDF file is corrupted or unreadable");
        }
        return errors;
    }

    private void validateBasicChecks(MultipartFile file, List<String> errors) {
        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            errors.add("File size exceeds 10MB limit");
        }

        // Check MIME type (client-supplied, not trusted alone)
        String contentType = file.getContentType();
        if (!"application/pdf".equals(contentType)) {
            errors.add("Only PDF files are allowed");
        }

        // Check file extension
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            errors.add("Only PDF files are allowed (.pdf extension required)");
        }

        // Verify actual file content using magic bytes (%PDF)
        if (!hasPdfMagicBytes(file)) {
            errors.add("File content does not appear to be a valid PDF");
        }
    }

    private boolean hasPdfMagicBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[PDF_MAGIC_BYTES.length];
            int read = is.read(header);
            if (read < PDF_MAGIC_BYTES.length) return false;
            for (int i = 0; i < PDF_MAGIC_BYTES.length; i++) {
                if (header[i] != PDF_MAGIC_BYTES[i]) return false;
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void validateFreeAgentFormat(PDDocument document, List<String> errors) {
        // TODO: Implement FreeAgent-specific PDF format validation
        // This would check for FreeAgent template structure, specific text patterns, etc.
        // For now, we'll allow any PDF that is readable
        // In a real implementation, you would:
        // 1. Extract text using PDFBox
        // 2. Check for FreeAgent-specific text patterns (e.g., "FreeAgent", "Invoice", etc.)
        // 3. Validate the layout matches expected FreeAgent template
    }

    private void validateQuickBooksFormat(PDDocument document, List<String> errors) {
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            boolean hasInvoiceNumber = Pattern.compile("INVOICE\\s+\\d+").matcher(text).find();
            boolean hasDate = Pattern.compile("\\bDATE\\s+\\d{2}/\\d{2}/\\d{4}").matcher(text).find();
            boolean hasTotals = text.contains("SUBTOTAL") || text.contains("BALANCE DUE");

            if (!hasInvoiceNumber) {
                errors.add("PDF does not appear to be a QuickBooks invoice (no invoice number found)");
            }
            if (!hasDate) {
                errors.add("PDF does not contain a recognisable QuickBooks date format (DD/MM/YYYY)");
            }
            if (!hasTotals) {
                errors.add("PDF does not contain QuickBooks totals section (SUBTOTAL / BALANCE DUE)");
            }
        } catch (IOException e) {
            errors.add("Could not read PDF text content for QuickBooks validation");
        }
    }
}
