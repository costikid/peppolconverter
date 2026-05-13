package com.bromleywebworks.peppol;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // --- File type / content validation ---

    @Test
    void apiConvert_rejectsTextFileWithPdfContentType() throws Exception {
        MockMultipartFile fakeFile = new MockMultipartFile(
                "file", "evil.pdf", "application/pdf",
                "This is just plain text, not a PDF".getBytes());

        mockMvc.perform(multipart("/api/convert").file(fakeFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("invalid_file"));
    }

    @Test
    void apiConvert_rejectsNonPdfExtension() throws Exception {
        byte[] fakePdfContent = "%PDF-fake content".getBytes();
        MockMultipartFile fakeFile = new MockMultipartFile(
                "file", "malicious.exe", "application/pdf", fakePdfContent);

        mockMvc.perform(multipart("/api/convert").file(fakeFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("invalid_file"));
    }

    @Test
    void apiConvert_rejectsWrongContentType() throws Exception {
        byte[] fakePdfContent = "%PDF-fake content".getBytes();
        MockMultipartFile fakeFile = new MockMultipartFile(
                "file", "document.pdf", "text/html", fakePdfContent);

        mockMvc.perform(multipart("/api/convert").file(fakeFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("invalid_file"));
    }

    @Test
    void apiConvert_rejectsEmptyFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/convert").file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apiConvert_rejectsMissingFile() throws Exception {
        mockMvc.perform(multipart("/api/convert")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest());
    }

    // --- Header injection protection ---

    @Test
    void apiConvert_maliciousFilenameDoesNotInjectHeader() throws Exception {
        // A real valid PDF is needed to get past validation; use a crafted minimal PDF
        byte[] maliciousPdf = createMinimalPdf();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoice\r\nX-Injected-Header: evil.pdf",
                "application/pdf",
                maliciousPdf);

        // Even if it gets through, the Content-Disposition should not contain CRLF
        mockMvc.perform(multipart("/api/convert").file(file))
                // Regardless of status, the response headers must not contain injected header
                .andExpect(header().doesNotExist("X-Injected-Header"));
    }

    // --- Web UI upload validation ---

    @Test
    void webUpload_rejectsNonPdfFile() throws Exception {
        MockMultipartFile fakeFile = new MockMultipartFile(
                "pdfFile", "notapdf.txt", "text/plain",
                "This is not a PDF".getBytes());

        mockMvc.perform(multipart("/freeagent-to-peppol/upload").file(fakeFile))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/freeagent-to-peppol/upload"));
    }

    @Test
    void webUpload_rejectsFileWithInvalidMagicBytes() throws Exception {
        MockMultipartFile fakeFile = new MockMultipartFile(
                "pdfFile", "fake.pdf", "application/pdf",
                "Not a real PDF file content".getBytes());

        mockMvc.perform(multipart("/freeagent-to-peppol/upload").file(fakeFile))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/freeagent-to-peppol/upload"));
    }

    // --- Helper to create a minimal (but structurally valid for magic bytes) fake PDF ---

    private byte[] createMinimalPdf() {
        // A minimal valid-ish PDF just enough to pass magic byte check but fail extraction
        String minPdf = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\nxref\n0 2\n"
                + "0000000000 65535 f\n0000000009 00000 n\ntrailer\n<< /Size 2 /Root 1 0 R >>\n"
                + "startxref\n9\n%%EOF";
        return minPdf.getBytes();
    }
}
