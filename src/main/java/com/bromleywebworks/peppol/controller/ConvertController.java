package com.bromleywebworks.peppol.controller;

import com.bromleywebworks.peppol.dto.ConvertRequest;
import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import com.bromleywebworks.peppol.service.ExtractionService;
import com.bromleywebworks.peppol.service.FileUploadValidator;
import com.bromleywebworks.peppol.service.MappingService;
import com.bromleywebworks.peppol.service.ValidationService;
import com.helger.ubl21.UBL21Writer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ConvertController {

    private final ExtractionService extractionService;
    private final MappingService mappingService;
    private final ValidationService validationService;
    private final FileUploadValidator fileUploadValidator;

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> convert(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "converterType", defaultValue = "freeagent") String converterType,
            @RequestParam(value = "metadata", required = false) String metadataJson) {

        try {
            log.info("Received conversion request for file: {}, converterType: {}", file.getOriginalFilename(), converterType);

            // Validate file before processing
            List<String> fileErrors = fileUploadValidator.validate(file);
            if (!fileErrors.isEmpty()) {
                log.warn("File validation failed: {}", fileErrors);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "invalid_file");
                errorResponse.put("errors", fileErrors);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }

            // Phase 2: Extract
            ExtractedInvoice extracted = extractionService.extract(file, converterType);
            log.info("Extracted invoice number: {}, buyer: {}",
                    extracted.getInvoiceNumber(),
                    extracted.getBuyer() != null ? extracted.getBuyer().getName() : "null");

            // Phase 3: Map
            ConvertRequest request = parseMetadata(metadataJson);
            InvoiceType invoice = mappingService.map(extracted, request);

            // Phase 4: Validate
            List<String> errors = validationService.validate(invoice);
            if (!errors.isEmpty()) {
                log.warn("Validation failed with {} errors", errors.size());
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "validation_failed");
                errorResponse.put("errors", errors);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }

            // Write XML
            String xml = UBL21Writer.invoice().getAsString(invoice);
            log.info("Successfully converted invoice {} to Peppol XML", extracted.getInvoiceNumber());

            String safeInvoiceNumber = sanitizeForHeader(extracted.getInvoiceNumber());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .header("Content-Disposition",
                            "attachment; filename=\"invoice-" + safeInvoiceNumber + ".xml\"")
                    .body(xml);

        } catch (Exception e) {
            log.error("Conversion failed", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    private String sanitizeForHeader(String value) {
        if (value == null) return "unknown";
        // Remove CRLF and any non-alphanumeric characters except dash/underscore
        return value.replaceAll("[\\r\\n]", "").replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private ConvertRequest parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isEmpty()) {
            return new ConvertRequest();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(metadataJson, ConvertRequest.class);
        } catch (Exception e) {
            log.warn("Could not parse metadata JSON, using defaults: {}", e.getMessage());
            return new ConvertRequest();
        }
    }
}
