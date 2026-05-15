package com.bromleywebworks.peppol.controller;

import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentInvoiceSummary;
import com.bromleywebworks.peppol.service.FreeAgentApiClient;
import com.bromleywebworks.peppol.service.MappingService;
import com.bromleywebworks.peppol.service.ValidationService;
import com.bromleywebworks.peppol.service.strategy.FreeAgentApiExtractionStrategy;
import com.helger.ubl21.UBL21Writer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@ConditionalOnBean(FreeAgentApiClient.class)
@RequestMapping("/freeagent")
public class FreeAgentController {

    private final FreeAgentApiClient apiClient;
    private final FreeAgentApiExtractionStrategy extractionStrategy;
    private final MappingService mappingService;
    private final ValidationService validationService;

    @GetMapping("/login")
    public String login() {
        return "freeagent-login";
    }

    @GetMapping("/invoices")
    public String invoices(
            @RegisteredOAuth2AuthorizedClient("freeagent") OAuth2AuthorizedClient authorizedClient,
            @AuthenticationPrincipal OAuth2User oauth2User,
            @RequestParam(defaultValue = "1") int page,
            Model model) {
        
        try {
            // OAuth2 filter function handles token refresh automatically
            List<FreeAgentInvoiceSummary> invoices = apiClient
                .listInvoices(page, 25)
                .collectList()
                .block();
            
            model.addAttribute("invoices", invoices);
            model.addAttribute("currentPage", page);
            model.addAttribute("userName", oauth2User.getAttribute("name"));
            
            return "freeagent-invoices";
        } catch (WebClientResponseException e) {
            log.error("Error fetching invoices: {}", e.getMessage());
            model.addAttribute("error", "Failed to fetch invoices from FreeAgent");
            return "freeagent-invoices";
        }
    }

    @GetMapping("/convert/{invoiceId}")
    public ResponseEntity<?> convertInvoice(
            @RegisteredOAuth2AuthorizedClient("freeagent") OAuth2AuthorizedClient authorizedClient,
            @PathVariable String invoiceId) {
        
        try {
            log.info("Converting FreeAgent invoice: {}", invoiceId);
            
            // Extract from API (OAuth2 filter handles tokens)
            ExtractedInvoice extracted = extractionStrategy
                .extractFromApi(invoiceId)
                .block();
            
            // Map to UBL
            InvoiceType invoice = mappingService.map(extracted, new com.bromleywebworks.peppol.dto.ConvertRequest());
            
            // Validate
            List<String> errors = validationService.validate(invoice);
            if (!errors.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "validation_failed");
                errorResponse.put("errors", errors);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }
            
            // Write XML
            String xml = UBL21Writer.invoice().getAsString(invoice);
            
            // XSD Schema Validation (Peppol BIS 3.0 compliance)
            // Note: ph-ubl21 library includes built-in validation. If stricter validation needed,
            // add explicit XSD validation against Peppol BIS 3.0 schema.
            // For now, rely on ValidationService which already validates UBL structure.
            
            log.info("Successfully converted FreeAgent invoice {} to Peppol XML", invoiceId);
            
            String safeInvoiceNumber = sanitizeForHeader(extracted.getInvoiceNumber());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .header("Content-Disposition",
                            "attachment; filename=\"invoice-" + safeInvoiceNumber + ".xml\"")
                    .body(xml);
                    
        } catch (Exception e) {
            log.error("Conversion failed for invoice {}", invoiceId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    private String sanitizeForHeader(String value) {
        if (value == null) return "unknown";
        return value.replaceAll("[\\r\\n]", "").replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
