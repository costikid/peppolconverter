package com.bromleywebworks.peppol.controller;

import com.bromleywebworks.peppol.dto.BlogPost;
import com.bromleywebworks.peppol.dto.ConvertRequest;
import com.bromleywebworks.peppol.dto.ConverterType;
import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import com.bromleywebworks.peppol.dto.UploadForm;
import com.bromleywebworks.peppol.service.BlogService;
import com.bromleywebworks.peppol.service.ExtractionService;
import com.bromleywebworks.peppol.service.FileUploadValidator;
import com.bromleywebworks.peppol.service.MappingService;
import com.bromleywebworks.peppol.service.ValidationService;
import com.helger.ubl21.UBL21Writer;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
public class WebController {

    private static final Logger log = LoggerFactory.getLogger(WebController.class);

    private final FileUploadValidator fileUploadValidator;
    private final ExtractionService extractionService;
    private final MappingService mappingService;
    private final ValidationService validationService;
    private final BlogService blogService;

    public WebController(FileUploadValidator fileUploadValidator,
                         ExtractionService extractionService,
                         MappingService mappingService,
                         ValidationService validationService,
                         BlogService blogService) {
        this.fileUploadValidator = fileUploadValidator;
        this.extractionService = extractionService;
        this.mappingService = mappingService;
        this.validationService = validationService;
        this.blogService = blogService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "Peppol Converter - Home");
        model.addAttribute("description", "Convert accounting invoices to Peppol BIS Billing 3.0");
        model.addAttribute("canonicalUrl", "https://localhost:8080/");
        return "home";
    }

    @GetMapping("/about")
    public String about(Model model) {
        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("About", "/about", true));

        model.addAttribute("title", "About Peppol Converter");
        model.addAttribute("description", "Learn about the Peppol Converter tool and its developer");
        model.addAttribute("canonicalUrl", "https://localhost:8080/about");
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        return "about";
    }

    @GetMapping("/privacy")
    public String privacy(Model model) {
        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("Privacy", "/privacy", true));

        model.addAttribute("title", "Privacy Policy");
        model.addAttribute("description", "How we handle your data");
        model.addAttribute("canonicalUrl", "https://localhost:8080/privacy");
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        return "privacy";
    }

    @GetMapping("/terms")
    public String terms(Model model) {
        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("Terms", "/terms", true));

        model.addAttribute("title", "Terms of Service");
        model.addAttribute("description", "Terms and conditions for using Peppol Converter");
        model.addAttribute("canonicalUrl", "https://localhost:8080/terms");
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        return "terms";
    }

    @GetMapping("/contact")
    public String contact(Model model) {
        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("Contact", "/contact", true));

        model.addAttribute("title", "Contact");
        model.addAttribute("description", "Get in touch");
        model.addAttribute("canonicalUrl", "https://localhost:8080/contact");
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        return "contact";
    }

    @GetMapping("/how-it-works")
    public String howItWorks(Model model) {
        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("How It Works", "/how-it-works", true));

        model.addAttribute("title", "How It Works - Peppol Converter");
        model.addAttribute("description", "Learn how to convert your accounting invoices to Peppol BIS Billing 3.0");
        model.addAttribute("canonicalUrl", "https://localhost:8080/how-it-works");
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        return "how-it-works";
    }

    @GetMapping("/faq")
    public String faq(Model model) {
        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("FAQ", "/faq", true));

        model.addAttribute("title", "FAQ - Peppol Converter");
        model.addAttribute("description", "Frequently asked questions about Peppol Converter");
        model.addAttribute("canonicalUrl", "https://localhost:8080/faq");
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        return "faq";
    }

    @GetMapping("/blog")
    public String blog(Model model) {
        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("Blog", "/blog", true));

        model.addAttribute("title", "Blog - Peppol Converter");
        model.addAttribute("description", "Articles and updates about Peppol e-invoicing");
        model.addAttribute("canonicalUrl", "https://localhost:8080/blog");
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        model.addAttribute("posts", blogService.getAllPosts());
        return "blog/index";
    }

    @GetMapping("/blog/{slug}")
    public String blogPost(@PathVariable String slug, Model model) {
        return blogService.getPost(slug).map(post -> {
            List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
            breadcrumbItems.add(new BreadcrumbItem("Blog", "/blog", false));
            breadcrumbItems.add(new BreadcrumbItem(post.getTitle(), "/blog/" + slug, true));

            model.addAttribute("title", post.getTitle() + " - Peppol Converter");
            model.addAttribute("description", post.getSummary());
            model.addAttribute("canonicalUrl", "https://localhost:8080/blog/" + slug);
            model.addAttribute("breadcrumbItems", breadcrumbItems);
            model.addAttribute("post", post);
            return "blog/post";
        }).orElse("redirect:/blog");
    }

    @GetMapping("/freeagent-to-peppol")
    public String freeagentLanding(Model model) {
        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("FreeAgent to Peppol", "/freeagent-to-peppol", true));

        model.addAttribute("title", "FreeAgent to Peppol Converter");
        model.addAttribute("description", "Convert FreeAgent PDF invoices to Peppol BIS Billing 3.0 UBL XML");
        model.addAttribute("canonicalUrl", "https://localhost:8080/freeagent-to-peppol");
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        return "freeagent/landing";
    }

    @GetMapping("/freeagent-to-peppol/upload")
    public String freeagentUpload(Model model) {
        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("FreeAgent to Peppol", "/freeagent-to-peppol", false));
        breadcrumbItems.add(new BreadcrumbItem("Upload", "/freeagent-to-peppol/upload", true));

        model.addAttribute("title", "Upload FreeAgent Invoice");
        model.addAttribute("description", "Upload your FreeAgent PDF invoice to convert to Peppol");
        model.addAttribute("canonicalUrl", "https://localhost:8080/freeagent-to-peppol/upload");
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        model.addAttribute("uploadForm", new UploadForm());
        return "freeagent/upload";
    }

    @PostMapping("/freeagent-to-peppol/upload")
    public String processUpload(
            @ModelAttribute UploadForm uploadForm,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // Validate the file
        List<String> errors = fileUploadValidator.validate(uploadForm, ConverterType.FREEAGENT);
        if (!errors.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", String.join(", ", errors));
            return "redirect:/freeagent-to-peppol/upload";
        }

        // Run conversion synchronously and redirect straight to result
        String sessionId = java.util.UUID.randomUUID().toString();
        try {
            ExtractedInvoice extracted = extractionService.extract(uploadForm.getPdfFile());

            ConvertRequest request = new ConvertRequest();
            request.setBuyerEndpoint(uploadForm.getBuyerEndpoint());
            request.setBuyerScheme(uploadForm.getBuyerScheme());
            request.setDueDate(uploadForm.getDueDate());
            request.setCurrency(uploadForm.getCurrency());
            request.setVatCategory(uploadForm.getVatCategory());

            InvoiceType invoice = mappingService.map(extracted, request);

            List<String> validationErrors = validationService.validate(invoice);
            if (!validationErrors.isEmpty()) {
                redirectAttributes.addFlashAttribute("error",
                        "Validation failed: " + String.join("; ", validationErrors));
                return "redirect:/freeagent-to-peppol/upload";
            }

            String xmlOutput = UBL21Writer.invoice().getAsString(invoice);
            session.setAttribute("sessionId", sessionId);
            session.setAttribute("xmlOutput", xmlOutput);
            session.setAttribute("invoiceNumber", extracted.getInvoiceNumber());

        } catch (Exception e) {
            log.error("Conversion failed", e);
            redirectAttributes.addFlashAttribute("error", "Conversion failed: " + e.getMessage());
            return "redirect:/freeagent-to-peppol/upload";
        }

        return "redirect:/freeagent-to-peppol/result/" + sessionId;
    }

    @GetMapping("/freeagent-to-peppol/status/{sessionId}")
    public String freeagentStatus(
            @PathVariable String sessionId,
            Model model,
            HttpSession session) {

        // Verify session exists
        if (!sessionId.equals(session.getAttribute("sessionId"))) {
            return "redirect:/freeagent-to-peppol/upload";
        }

        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("FreeAgent to Peppol", "/freeagent-to-peppol", false));
        breadcrumbItems.add(new BreadcrumbItem("Status", "/freeagent-to-peppol/status/" + sessionId, true));

        model.addAttribute("title", "Processing Invoice");
        model.addAttribute("description", "Your FreeAgent invoice is being converted to Peppol");
        model.addAttribute("canonicalUrl", "https://localhost:8080/freeagent-to-peppol/status/" + sessionId);
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        model.addAttribute("sessionId", sessionId);

        // TODO: Check actual conversion status
        // For now, we'll redirect to result after a delay
        // In real implementation, this would check the conversion service status
        return "freeagent/status";
    }

    @GetMapping("/freeagent-to-peppol/result/{sessionId}")
    public String freeagentResult(
            @PathVariable String sessionId,
            Model model,
            HttpSession session) {

        // Verify session exists
        if (!sessionId.equals(session.getAttribute("sessionId"))) {
            return "redirect:/freeagent-to-peppol/upload";
        }

        String xmlOutput = (String) session.getAttribute("xmlOutput");
        String invoiceNumber = (String) session.getAttribute("invoiceNumber");

        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("FreeAgent to Peppol", "/freeagent-to-peppol", false));
        breadcrumbItems.add(new BreadcrumbItem("Result", "/freeagent-to-peppol/result/" + sessionId, true));

        model.addAttribute("title", "Conversion Complete");
        model.addAttribute("description", "Your FreeAgent invoice has been converted to Peppol");
        model.addAttribute("canonicalUrl", "https://localhost:8080/freeagent-to-peppol/result/" + sessionId);
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("xmlOutput", xmlOutput);
        model.addAttribute("invoiceNumber", invoiceNumber);

        return "freeagent/result";
    }

    @GetMapping("/freeagent-to-peppol/download/{sessionId}")
    public ResponseEntity<byte[]> downloadXml(
            @PathVariable String sessionId,
            HttpSession session) {

        if (!sessionId.equals(session.getAttribute("sessionId"))) {
            return ResponseEntity.badRequest().build();
        }

        String xmlOutput = (String) session.getAttribute("xmlOutput");
        String invoiceNumber = (String) session.getAttribute("invoiceNumber");
        String filename = invoiceNumber != null ? "invoice-" + invoiceNumber + ".xml" : "peppol-invoice.xml";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(xmlOutput != null ? xmlOutput.getBytes() : new byte[0]);
    }

    public static class BreadcrumbItem {
        private final String name;
        private final String url;
        private final boolean last;

        public BreadcrumbItem(String name, String url, boolean last) {
            this.name = name;
            this.url = url;
            this.last = last;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }

        public boolean isLast() {
            return last;
        }
    }
}
