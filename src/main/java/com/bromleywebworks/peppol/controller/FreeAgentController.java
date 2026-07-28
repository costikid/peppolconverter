package com.bromleywebworks.peppol.controller;

import com.bromleywebworks.peppol.dto.ConvertRequest;
import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentCompany;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentContact;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentInvoice;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentInvoiceSummary;
import com.bromleywebworks.peppol.entity.ConvertedInvoice;
import com.bromleywebworks.peppol.exception.MissingIdentifierException;
import com.bromleywebworks.peppol.service.FreeAgentApiService;
import com.bromleywebworks.peppol.service.FreeAgentInvoiceMapper;
import com.bromleywebworks.peppol.service.InvoiceStorageService;
import com.bromleywebworks.peppol.service.MappingService;
import com.bromleywebworks.peppol.service.ValidationService;
import com.helger.ubl21.UBL21Writer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class FreeAgentController {

    private final FreeAgentApiService freeAgentApiService;
    private final FreeAgentInvoiceMapper freeAgentInvoiceMapper;
    private final MappingService mappingService;
    private final ValidationService validationService;
    private final InvoiceStorageService invoiceStorageService;

    @GetMapping("/freeagent-login")
    public String loginPage(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal != null) {
            return "redirect:/freeagent/invoices";
        }
        model.addAttribute("title", "Connect FreeAgent - Peppol Converter");
        model.addAttribute("description", "Connect your FreeAgent account to convert invoices to Peppol");
        model.addAttribute("canonicalUrl", "https://localhost:8080/freeagent-login");
        return "freeagent-login";
    }

    @GetMapping("/freeagent/invoices")
    public String listInvoices(
            @RegisteredOAuth2AuthorizedClient("freeagent") OAuth2AuthorizedClient authorizedClient,
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam(value = "page", defaultValue = "1") int page,
            Model model) {

        if (authorizedClient == null) {
            return "redirect:/freeagent-login";
        }

        try {
            List<FreeAgentInvoiceSummary> invoices = freeAgentApiService.listInvoices(
                    authorizedClient.getAccessToken(), page);

            String userName = "User";
            if (principal != null && principal.getAttribute("first_name") != null) {
                userName = principal.getAttribute("first_name");
            } else if (principal != null && principal.getAttribute("email") != null) {
                userName = principal.getAttribute("email");
            }

            model.addAttribute("title", "Your FreeAgent Invoices");
            model.addAttribute("description", "Browse and convert your FreeAgent invoices to Peppol");
            model.addAttribute("canonicalUrl", "https://localhost:8080/freeagent/invoices");
            model.addAttribute("invoices", invoices);
            model.addAttribute("userName", userName);
            model.addAttribute("currentPage", page);
            return "freeagent-invoices";
        } catch (Exception e) {
            log.error("Failed to fetch FreeAgent invoices", e);
            model.addAttribute("title", "Your FreeAgent Invoices");
            model.addAttribute("description", "Browse and convert your FreeAgent invoices to Peppol");
            model.addAttribute("error", "Could not fetch invoices from FreeAgent: " + e.getMessage());
            model.addAttribute("invoices", List.of());
            model.addAttribute("userName", "User");
            model.addAttribute("currentPage", page);
            return "freeagent-invoices";
        }
    }

    @GetMapping("/freeagent/convert/{id}")
    public String convertInvoice(
            @PathVariable String id,
            @RegisteredOAuth2AuthorizedClient("freeagent") OAuth2AuthorizedClient authorizedClient,
            @AuthenticationPrincipal OAuth2User principal,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (authorizedClient == null) {
            return "redirect:/freeagent-login";
        }

        try {
            return doConversion(id, authorizedClient, principal, null, null, redirectAttributes);
        } catch (MissingIdentifierException e) {
            log.warn("Missing identifier for invoice {}: {}", id, e.getMessage());
            return showBuyerForm(id, authorizedClient, model, redirectAttributes);
        } catch (Exception e) {
            log.error("FreeAgent invoice conversion failed for id: {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Conversion failed: " + e.getMessage());
            return "redirect:/freeagent/invoices";
        }
    }

    @PostMapping("/freeagent/convert/{id}")
    public String convertInvoiceWithEndpoint(
            @PathVariable String id,
            @RegisteredOAuth2AuthorizedClient("freeagent") OAuth2AuthorizedClient authorizedClient,
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam("buyerEndpoint") String buyerEndpoint,
            @RequestParam("buyerScheme") String buyerScheme,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (authorizedClient == null) {
            return "redirect:/freeagent-login";
        }

        try {
            return doConversion(id, authorizedClient, principal, buyerEndpoint, buyerScheme, redirectAttributes);
        } catch (MissingIdentifierException e) {
            log.warn("Still missing identifier for invoice {}: {}", id, e.getMessage());
            return showBuyerForm(id, authorizedClient, model, redirectAttributes);
        } catch (Exception e) {
            log.error("FreeAgent invoice conversion failed for id: {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Conversion failed: " + e.getMessage());
            return "redirect:/freeagent/invoices";
        }
    }

    private String doConversion(String id, OAuth2AuthorizedClient authorizedClient,
                                OAuth2User principal, String buyerEndpoint, String buyerScheme,
                                RedirectAttributes redirectAttributes) {
        log.info("Converting FreeAgent invoice id: {}", id);

        FreeAgentInvoice faInvoice = freeAgentApiService.getInvoice(authorizedClient.getAccessToken(), id);
        FreeAgentContact faContact = null;
        if (faInvoice.getContact() != null && !faInvoice.getContact().isEmpty()) {
            faContact = freeAgentApiService.getContact(authorizedClient.getAccessToken(), faInvoice.getContact());
        }
        FreeAgentCompany faCompany = freeAgentApiService.getCompany(authorizedClient.getAccessToken());

        ExtractedInvoice extracted = freeAgentInvoiceMapper.mapToExtractedInvoice(faInvoice, faContact, faCompany);
        ConvertRequest request = freeAgentInvoiceMapper.buildConvertRequest(extracted);

        if (buyerEndpoint != null && !buyerEndpoint.isEmpty()) {
            request.setBuyerEndpoint(buyerEndpoint);
        }
        if (buyerScheme != null && !buyerScheme.isEmpty()) {
            request.setBuyerScheme(buyerScheme);
        }

        InvoiceType invoice = mappingService.map(extracted, request);

        List<String> validationErrors = validationService.validate(invoice);
        if (!validationErrors.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "Validation failed: " + String.join("; ", validationErrors));
            return "redirect:/freeagent/invoices";
        }

        String xmlOutput = UBL21Writer.invoice().getAsString(invoice);

        Object idAttr = principal.getAttribute("id");
        String userId = idAttr != null ? idAttr.toString() : principal.getAttribute("email");
        ConvertedInvoice saved = invoiceStorageService.save(extracted, xmlOutput, userId);

        log.info("Successfully converted FreeAgent invoice {} to Peppol XML (saved as id={})", id, saved.getId());
        return "redirect:/freeagent-to-peppol/result/" + saved.getId();
    }

    private String showBuyerForm(String id, OAuth2AuthorizedClient authorizedClient,
                                 Model model, RedirectAttributes redirectAttributes) {
        try {
            FreeAgentInvoice faInvoice = freeAgentApiService.getInvoice(authorizedClient.getAccessToken(), id);
            FreeAgentContact faContact = null;
            if (faInvoice.getContact() != null && !faInvoice.getContact().isEmpty()) {
                faContact = freeAgentApiService.getContact(authorizedClient.getAccessToken(), faInvoice.getContact());
            }

            String buyerName = "Unknown Buyer";
            String buyerVatNumber = null;
            if (faContact != null) {
                buyerName = faContact.getOrganisationName() != null && !faContact.getOrganisationName().isEmpty()
                        ? faContact.getOrganisationName()
                        : "Unknown Buyer";
                buyerVatNumber = faContact.getSalesTaxRegistrationNumber();
            }

            model.addAttribute("title", "Buyer Endpoint Required - Peppol Converter");
            model.addAttribute("description", "Enter buyer Peppol endpoint details");
            model.addAttribute("invoiceId", id);
            model.addAttribute("buyerName", buyerName);
            model.addAttribute("buyerVatNumber", buyerVatNumber);
            return "freeagent-buyer-form";
        } catch (Exception e) {
            log.error("Failed to load buyer form for invoice {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Failed to load invoice details: " + e.getMessage());
            return "redirect:/freeagent/invoices";
        }
    }

    @GetMapping("/freeagent/my-invoices")
    public String mySavedInvoices(
            @AuthenticationPrincipal OAuth2User principal,
            Model model) {

        Object idAttr = principal.getAttribute("id");
        String userId = idAttr != null ? idAttr.toString() : principal.getAttribute("email");
        List<ConvertedInvoice> invoices = invoiceStorageService.listForUser(userId);

        List<WebController.BreadcrumbItem> breadcrumbItems = new java.util.ArrayList<>();
        breadcrumbItems.add(new WebController.BreadcrumbItem("FreeAgent to Peppol", "/freeagent-to-peppol", false));
        breadcrumbItems.add(new WebController.BreadcrumbItem("My Invoices", "/freeagent/my-invoices", true));

        model.addAttribute("title", "My Converted Invoices");
        model.addAttribute("description", "Your previously converted Peppol XML invoices");
        model.addAttribute("canonicalUrl", "https://localhost:8080/freeagent/my-invoices");
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        model.addAttribute("invoices", invoices);
        return "freeagent/my-invoices";
    }
}
