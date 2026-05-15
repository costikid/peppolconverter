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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public String freeagentUpload(
            @RequestParam(value = "extractedBuyerName", required = false) String buyerName,
            @RequestParam(value = "extractedBuyerStreet", required = false) String buyerStreet,
            @RequestParam(value = "extractedBuyerCity", required = false) String buyerCity,
            @RequestParam(value = "extractedBuyerPostcode", required = false) String buyerPostcode,
            @RequestParam(value = "extractedBuyerCountry", required = false) String buyerCountry,
            Model model) {

        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("FreeAgent to Peppol", "/freeagent-to-peppol", false));
        breadcrumbItems.add(new BreadcrumbItem("Upload", "/freeagent-to-peppol/upload", true));

        model.addAttribute("title", "Upload FreeAgent Invoice");
        model.addAttribute("description", "Upload your FreeAgent PDF invoice to convert to Peppol");
        model.addAttribute("canonicalUrl", "https://localhost:8080/freeagent-to-peppol/upload");
        model.addAttribute("breadcrumbItems", breadcrumbItems);

        UploadForm uploadForm = new UploadForm();
        if (buyerStreet != null && !buyerStreet.isEmpty()) uploadForm.setBuyerStreet(buyerStreet);
        if (buyerCity != null && !buyerCity.isEmpty()) uploadForm.setBuyerCity(buyerCity);
        if (buyerPostcode != null && !buyerPostcode.isEmpty()) uploadForm.setBuyerPostcode(buyerPostcode);
        if (buyerCountry != null && !buyerCountry.isEmpty()) uploadForm.setBuyerCountryCode(buyerCountry);

        model.addAttribute("uploadForm", uploadForm);
        model.addAttribute("extractedBuyerName", buyerName);
        model.addAttribute("hasExtractedData",
                buyerStreet != null || buyerCity != null || buyerPostcode != null);
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
            request.setBuyerStreet(uploadForm.getBuyerStreet());
            request.setBuyerCity(uploadForm.getBuyerCity());
            request.setBuyerPostcode(uploadForm.getBuyerPostcode());
            request.setBuyerCountryCode(uploadForm.getBuyerCountryCode());


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

    @PostMapping("/freeagent-to-peppol/extract")
    public ResponseEntity<?> extractFreeAgentPdfData(@RequestParam("file") MultipartFile file) {
        try {
            ExtractedInvoice extracted = extractionService.extract(file, "freeagent");
            ExtractedInvoice.Party buyer = extracted.getBuyer();

            Map<String, Object> response = new HashMap<>();
            response.put("invoiceNumber", extracted.getInvoiceNumber());
            response.put("issueDate", extracted.getIssueDate() != null ? extracted.getIssueDate().toString() : null);
            response.put("dueDate", extracted.getDueDate() != null ? extracted.getDueDate().toString() : null);
            response.put("totalAmount", extracted.getTotalAmount());
            response.put("currency", extracted.getCurrency());

            if (buyer != null) {
                response.put("buyerName", buyer.getName());
                response.put("buyerStreet", buyer.getStreet());
                response.put("buyerCity", buyer.getCity());
                response.put("buyerPostcode", buyer.getPostcode());
                response.put("buyerCountry", buyer.getCountry());
                response.put("buyerCountryCode", buyer.getCountryCode());
                response.put("buyerRegion", buyer.getRegion());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("FreeAgent PDF extraction failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/quickbooks-to-peppol/extract")
    public ResponseEntity<?> extractQuickBooksPdfData(@RequestParam("file") MultipartFile file) {
        try {
            ExtractedInvoice extracted = extractionService.extract(file, "quickbooks");
            ExtractedInvoice.Party buyer = extracted.getBuyer();

            Map<String, Object> response = new HashMap<>();
            response.put("invoiceNumber", extracted.getInvoiceNumber());
            response.put("issueDate", extracted.getIssueDate() != null ? extracted.getIssueDate().toString() : null);
            response.put("dueDate", extracted.getDueDate() != null ? extracted.getDueDate().toString() : null);
            response.put("totalAmount", extracted.getTotalAmount());
            response.put("currency", extracted.getCurrency());

            if (buyer != null) {
                response.put("buyerName", buyer.getName());
                response.put("buyerStreet", buyer.getStreet());
                response.put("buyerCity", buyer.getCity());
                response.put("buyerPostcode", buyer.getPostcode());
                response.put("buyerCountry", buyer.getCountry());
                response.put("buyerCountryCode", buyer.getCountryCode());
                response.put("buyerRegion", buyer.getRegion());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("QuickBooks PDF extraction failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/quickbooks-to-peppol")
    public String quickbooksLanding(Model model) {
        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("QuickBooks to Peppol", "/quickbooks-to-peppol", true));

        model.addAttribute("title", "QuickBooks to Peppol Converter");
        model.addAttribute("description", "Convert QuickBooks PDF invoices to Peppol BIS Billing 3.0 UBL XML");
        model.addAttribute("canonicalUrl", "https://localhost:8080/quickbooks-to-peppol");
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        return "quickbooks/landing";
    }

    @GetMapping("/quickbooks-to-peppol/upload")
    public String quickbooksUpload(Model model) {
        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("QuickBooks to Peppol", "/quickbooks-to-peppol", false));
        breadcrumbItems.add(new BreadcrumbItem("Upload", "/quickbooks-to-peppol/upload", true));

        model.addAttribute("title", "Upload QuickBooks Invoice");
        model.addAttribute("description", "Upload your QuickBooks PDF invoice to convert to Peppol");
        model.addAttribute("canonicalUrl", "https://localhost:8080/quickbooks-to-peppol/upload");
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        model.addAttribute("uploadForm", new UploadForm());
        return "quickbooks/upload";
    }

    @PostMapping("/quickbooks-to-peppol/upload")
    public String processQuickBooksUpload(
            @ModelAttribute UploadForm uploadForm,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        List<String> errors = fileUploadValidator.validate(uploadForm, ConverterType.QUICKBOOKS);
        if (!errors.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", String.join(", ", errors));
            return "redirect:/quickbooks-to-peppol/upload";
        }

        String sessionId = java.util.UUID.randomUUID().toString();
        try {
            ExtractedInvoice extracted = extractionService.extract(uploadForm.getPdfFile(), "quickbooks");

            ConvertRequest request = new ConvertRequest();
            request.setBuyerEndpoint(uploadForm.getBuyerEndpoint());
            request.setBuyerScheme(uploadForm.getBuyerScheme());
            request.setDueDate(uploadForm.getDueDate());
            request.setCurrency(uploadForm.getCurrency());
            request.setVatCategory(uploadForm.getVatCategory());
            request.setBuyerStreet(uploadForm.getBuyerStreet());
            request.setBuyerCity(uploadForm.getBuyerCity());
            request.setBuyerPostcode(uploadForm.getBuyerPostcode());
            request.setBuyerCountryCode(uploadForm.getBuyerCountryCode());


            InvoiceType invoice = mappingService.map(extracted, request);

            List<String> validationErrors = validationService.validate(invoice);
            if (!validationErrors.isEmpty()) {
                redirectAttributes.addFlashAttribute("error",
                        "Validation failed: " + String.join("; ", validationErrors));
                return "redirect:/quickbooks-to-peppol/upload";
            }

            String xmlOutput = UBL21Writer.invoice().getAsString(invoice);
            session.setAttribute("sessionId", sessionId);
            session.setAttribute("xmlOutput", xmlOutput);
            session.setAttribute("invoiceNumber", extracted.getInvoiceNumber());

        } catch (Exception e) {
            log.error("QuickBooks conversion failed", e);
            redirectAttributes.addFlashAttribute("error", "Conversion failed: " + e.getMessage());
            return "redirect:/quickbooks-to-peppol/upload";
        }

        return "redirect:/quickbooks-to-peppol/result/" + sessionId;
    }

    @GetMapping("/quickbooks-to-peppol/status/{sessionId}")
    public String quickbooksStatus(
            @PathVariable String sessionId,
            Model model,
            HttpSession session) {

        if (!sessionId.equals(session.getAttribute("sessionId"))) {
            return "redirect:/quickbooks-to-peppol/upload";
        }

        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("QuickBooks to Peppol", "/quickbooks-to-peppol", false));
        breadcrumbItems.add(new BreadcrumbItem("Status", "/quickbooks-to-peppol/status/" + sessionId, true));

        model.addAttribute("title", "Processing Invoice");
        model.addAttribute("description", "Your QuickBooks invoice is being converted to Peppol");
        model.addAttribute("canonicalUrl", "https://localhost:8080/quickbooks-to-peppol/status/" + sessionId);
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        model.addAttribute("sessionId", sessionId);
        return "quickbooks/status";
    }

    @GetMapping("/quickbooks-to-peppol/result/{sessionId}")
    public String quickbooksResult(
            @PathVariable String sessionId,
            Model model,
            HttpSession session) {

        if (!sessionId.equals(session.getAttribute("sessionId"))) {
            return "redirect:/quickbooks-to-peppol/upload";
        }

        String xmlOutput = (String) session.getAttribute("xmlOutput");
        String invoiceNumber = (String) session.getAttribute("invoiceNumber");

        List<BreadcrumbItem> breadcrumbItems = new ArrayList<>();
        breadcrumbItems.add(new BreadcrumbItem("QuickBooks to Peppol", "/quickbooks-to-peppol", false));
        breadcrumbItems.add(new BreadcrumbItem("Result", "/quickbooks-to-peppol/result/" + sessionId, true));

        model.addAttribute("title", "Conversion Complete");
        model.addAttribute("description", "Your QuickBooks invoice has been converted to Peppol");
        model.addAttribute("canonicalUrl", "https://localhost:8080/quickbooks-to-peppol/result/" + sessionId);
        model.addAttribute("breadcrumbItems", breadcrumbItems);
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("xmlOutput", xmlOutput);
        model.addAttribute("invoiceNumber", invoiceNumber);
        return "quickbooks/result";
    }

    @GetMapping("/quickbooks-to-peppol/download/{sessionId}")
    public ResponseEntity<byte[]> downloadQuickBooksXml(
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

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        String baseUrl = "https://localhost:8080";
        String lastmod = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);

        StringBuilder sitemap = new StringBuilder();
        sitemap.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sitemap.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        String[] urls = {
            "/",
            "/about",
            "/contact",
            "/faq",
            "/how-it-works",
            "/privacy",
            "/terms",
            "/blog",
            "/freeagent-to-peppol",
            "/quickbooks-to-peppol"
        };

        for (String url : urls) {
            sitemap.append("  <url>\n");
            sitemap.append("    <loc>").append(baseUrl).append(url).append("</loc>\n");
            sitemap.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
            sitemap.append("    <changefreq>weekly</changefreq>\n");
            sitemap.append("    <priority>0.8</priority>\n");
            sitemap.append("  </url>\n");
        }

        sitemap.append("</urlset>");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(sitemap.toString());
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
