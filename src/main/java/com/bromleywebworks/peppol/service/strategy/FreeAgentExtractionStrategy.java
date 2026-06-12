package com.bromleywebworks.peppol.service.strategy;

import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class FreeAgentExtractionStrategy implements ExtractionStrategy {

    private static final Pattern INVOICE_NUMBER = Pattern.compile("(?i)Invoice\\s*#?\\s*([A-Z]*\\d+)");
    private static final Pattern INVOICE_NUMBER_ALT = Pattern.compile("(?i)Invoice\\s+([A-Z]+\\d+)");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2})\\s+(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DUE_DATE = Pattern.compile("(?i)Payment\\s+due\\s+by\\s+(\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4})");
    private static final Pattern DATE_PATTERN_SLASH = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})");
    private static final Pattern DATE_PATTERN_DASH = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern LINE_ITEM = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s+(.+?)\\s+([\\d.,]+)\\s+([\\d.,]+)$");
    private static final Pattern LINE_ITEM_WITH_VAT = Pattern.compile(
            "^(\\d+(?:\\.\\d+)?)\\s+(.+?)\\s+([\\d.,]+)\\s+(\\d+(?:\\.\\d+)?)%\\s+([\\d.,]+)$");
    // More flexible total patterns
    private static final Pattern NET_TOTAL = Pattern.compile("(?i)Net\\s+Total[:\\s]*£?([\\d.,]+)");
    private static final Pattern TOTAL = Pattern.compile("(?i)(?:GBP\\s*)?Total[:\\s]*£?([\\d.,]+)");
    private static final Pattern GRAND_TOTAL = Pattern.compile("(?i)GBP\\s+Total[:\\s]*£?([\\d.,]+)");
    private static final Pattern PAID = Pattern.compile("(?i)Payment:\\s*(\\d{1,2}\\s+[A-Za-z]+\\s+\\d{2,4})\\s+£?([\\d.,]+)");
    private static final Pattern DUE = Pattern.compile("(?i)(?:GBP\\s*)?Due[:\\s]*£?([\\d.,]+)");
    private static final Pattern VAT_SUMMARY = Pattern.compile("(?i)VAT\\s*@?\\s*(\\d+(?:\\.\\d+)?)%?\\s+£?([\\d.,]+)");
    private static final Pattern VAT_AMOUNT_ONLY = Pattern.compile("(?i)^\\s*VAT\\s+£?([\\d.,]+)\\s*$");
    // Support both "Sort Code:" and "Bank/Sort Code:"
    private static final Pattern SORT_CODE = Pattern.compile("(?i)(?:Bank/)?Sort\\s+Code:\\s*([\\d-]{6,8})");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("(?i)Account\\s+Number:\\s*(\\d+)");
    private static final Pattern PAYMENT_REF = Pattern.compile("(?i)Payment\\s+Reference:\\s*(.+)");
    private static final Pattern BANK_NAME = Pattern.compile("^([A-Za-z]+)\\s*$");
    private static final Pattern EC_STATUS = Pattern.compile("(?i)(EC\\s+(Goods|Services)|Reverse\\s+Charge)");
    private static final Pattern DISCOUNT_LINE = Pattern.compile("(?i)^(\\d+(?:\\.\\d+)?)%\\s+Discount\\s+£?([\\d.,]+)$");
    private static final Pattern PAYMENT_METHOD_TEXT = Pattern.compile("(?i)(?:Accepted\\s+)?payment\\s+methods\\s+(?:are\\s+)?(?:made\\s+by\\s+)?(?:by\\s+)?(.+)");
    private static final Pattern BANK_TRANSFER = Pattern.compile("(?i)bank\\s+transfer|cheque");
    private static final Pattern PAYPAL = Pattern.compile("(?i)PayPal");
    private static final Pattern STRIPE = Pattern.compile("(?i)Stripe");
    private static final Pattern GOCARDLESS = Pattern.compile("(?i)GoCardless");

    private static final DateTimeFormatter FREE_AGENT_DATE = new DateTimeFormatterBuilder()
            .appendPattern("d MMMM yyyy")
            .toFormatter();

    private static final DateTimeFormatter SHORT_YEAR_DATE = new DateTimeFormatterBuilder()
            .appendPattern("d MMMM ")
            .appendValueReduced(ChronoField.YEAR, 2, 4, 2000)
            .toFormatter();

    private static final DateTimeFormatter SLASH_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DASH_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final long PDF_MAX_MAIN_MEMORY = 20 * 1024 * 1024L;

    @Override
    public String getSupportedType() {
        return "freeagent";
    }

    @Override
    public ExtractedInvoice extract(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream(),
                MemoryUsageSetting.setupMixed(PDF_MAX_MAIN_MEMORY))) {
            log.info("PDF loaded, pages: {}", document.getNumberOfPages());
            String fullText = extractFullText(document);
            log.info("Extracted text length: {} chars", fullText.length());
            log.debug("Extracted PDF text:\n{}", fullText);
            return parseFreeAgentText(fullText);
        } catch (Exception e) {
            log.error("Error extracting FreeAgent PDF: {}", e.getMessage(), e);
            throw e;
        }
    }

    private String extractFullText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        String text = stripper.getText(document);
        log.info("Extracted text from PDF:\n{}", text);
        return text;
    }

    private ExtractedInvoice parseFreeAgentText(String text) {
        ExtractedInvoice invoice = new ExtractedInvoice();
        invoice.setPaymentDetails(new ExtractedInvoice.PaymentDetails());
        String[] lines = text.split("\\r?\\n");

        log.debug("PDF text lines: {}", String.join(" | ", lines));
        parseSellerAndBuyer(lines, invoice);
        parseInvoiceMeta(text, invoice);
        parseLineItems(lines, invoice);
        parseTotals(text, invoice);
        parsePaymentDetails(lines, invoice);

        // Validate critical fields
        validateExtractedInvoice(invoice);

        // Validate tax calculations within tolerance
        validateTaxCalculations(invoice);

        log.info("Parsed FreeAgent invoice: number={}, seller={}, buyer={}",
                invoice.getInvoiceNumber(),
                invoice.getSeller() != null ? invoice.getSeller().getName() : "null",
                invoice.getBuyer() != null ? invoice.getBuyer().getName() : "null");

        return invoice;
    }

    private void validateExtractedInvoice(ExtractedInvoice invoice) {
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isEmpty()) {
            log.warn("Missing invoice number");
        }
        if (invoice.getIssueDate() == null) {
            log.warn("Missing issue date");
        }
        if (invoice.getSeller() == null || invoice.getSeller().getName() == null || invoice.getSeller().getName().isEmpty()) {
            log.warn("Missing seller name");
        }
        if (invoice.getBuyer() == null || invoice.getBuyer().getName() == null || invoice.getBuyer().getName().isEmpty()) {
            log.warn("Missing buyer name");
        }
        // Line items are optional for some invoice formats (e.g., credit notes, summary invoices)
        if (invoice.getLineItems() == null || invoice.getLineItems().isEmpty()) {
            log.info("No line items extracted - this is acceptable for some invoice formats");
        }
        if (invoice.getTotalAmount() == null || invoice.getTotalAmount().compareTo(BigDecimal.ZERO) == 0) {
            log.warn("Missing or zero total amount");
        }
    }

    private void validateTaxCalculations(ExtractedInvoice invoice) {
        // PEPPOL-EN16931-R004: Tax amounts must match within €0.02 tolerance
        if (invoice.getLineItems() != null && !invoice.getLineItems().isEmpty()) {
            BigDecimal calculatedLineTotal = invoice.getLineItems().stream()
                    .map(ExtractedInvoice.LineItem::getLineTotal)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (invoice.getTotalAmount() != null) {
                BigDecimal difference = calculatedLineTotal.subtract(invoice.getTotalAmount()).abs();
                BigDecimal tolerance = new BigDecimal("0.02");
                if (difference.compareTo(tolerance) > 0) {
                    log.warn("Tax calculation validation failed: Line total sum {} differs from invoice total {} by more than tolerance {}",
                            calculatedLineTotal, invoice.getTotalAmount(), difference);
                } else {
                    log.info("Tax calculation validation passed: Line total sum {} matches invoice total {} within tolerance",
                            calculatedLineTotal, invoice.getTotalAmount());
                }
            }

            // Validate individual line item calculations: quantity × unitPrice = lineTotal
            for (ExtractedInvoice.LineItem item : invoice.getLineItems()) {
                if (item.getQuantity() != null && item.getUnitPrice() != null && item.getLineTotal() != null) {
                    BigDecimal calculated = item.getQuantity().multiply(item.getUnitPrice());
                    BigDecimal difference = calculated.subtract(item.getLineTotal()).abs();
                    BigDecimal tolerance = new BigDecimal("0.02");
                    if (difference.compareTo(tolerance) > 0) {
                        log.warn("Line item {} calculation validation failed: {} × {} = {} but line total is {} (diff: {})",
                                item.getLineNumber(), item.getQuantity(), item.getUnitPrice(), calculated, item.getLineTotal(), difference);
                    }
                }
            }
        }

        // Validate total calculation: totalAmount + vatAmount = dueAmount (or payable amount)
        if (invoice.getTotalAmount() != null && invoice.getVatAmount() != null && invoice.getDueAmount() != null) {
            BigDecimal calculated = invoice.getTotalAmount().add(invoice.getVatAmount());
            BigDecimal difference = calculated.subtract(invoice.getDueAmount()).abs();
            BigDecimal tolerance = new BigDecimal("0.02");
            if (difference.compareTo(tolerance) > 0) {
                log.warn("Total calculation validation failed: {} + {} = {} but due amount is {} (diff: {})",
                        invoice.getTotalAmount(), invoice.getVatAmount(), calculated, invoice.getDueAmount(), difference);
            } else {
                log.info("Total calculation validation passed: {} + {} = {} matches due amount {}",
                        invoice.getTotalAmount(), invoice.getVatAmount(), calculated, invoice.getDueAmount());
            }
        }
    }

    private void parseSellerAndBuyer(String[] lines, ExtractedInvoice invoice) {
        ExtractedInvoice.Party seller = new ExtractedInvoice.Party();
        ExtractedInvoice.Party buyer = new ExtractedInvoice.Party();

        // Detect template type
        TemplateType templateType = detectTemplateType(lines);
        log.info("Detected template type: {}", templateType);

        switch (templateType) {
            case MEDIANODE_BUYER_LEFT:
                // Template 4: buyer top-left, seller top-right
                parseMediaNodeBuyerLeft(lines, seller, buyer);
                break;
            case MEDIANODE_LOGO_FIRST:
                // Templates 1-3: logo first, seller top-right, buyer left
                parseMediaNodeLogoFirst(lines, seller, buyer);
                break;
            case FREEAGENT_RIGHT_ALIGNED:
                int sellerEnd = parseSellerRightAligned(lines, seller);
                parseBuyerRightAligned(lines, buyer, sellerEnd);
                break;
            case FREEAGENT_LEFT_ALIGNED:
            default:
                int sellerEndLeft = parseSellerLeftAligned(lines, seller);
                parseBuyerLeftAligned(lines, buyer, sellerEndLeft);
                break;
        }

        if (seller.getCountryCode() == null || seller.getCountryCode().isEmpty()) {
            seller.setCountryCode("GB");
        }
        if (buyer.getCountryCode() == null || buyer.getCountryCode().isEmpty()) {
            buyer.setCountryCode("GB");
        }

        // Fallback for missing address fields - use name if address incomplete
        if (seller.getStreet() == null || seller.getStreet().isEmpty()) {
            log.warn("Seller street address missing, using name as fallback");
            seller.setStreet(seller.getName() != null ? seller.getName() : "Unknown");
        }
        if (buyer.getStreet() == null || buyer.getStreet().isEmpty()) {
            log.warn("Buyer street address missing, using name as fallback");
            buyer.setStreet(buyer.getName() != null ? buyer.getName() : "Unknown");
        }

        invoice.setSeller(seller);
        invoice.setBuyer(buyer);
    }

    private enum TemplateType {
        FREEAGENT_LEFT_ALIGNED,
        FREEAGENT_RIGHT_ALIGNED,
        MEDIANODE_LOGO_FIRST,
        MEDIANODE_BUYER_LEFT
    }

    private TemplateType detectTemplateType(String[] lines) {
        // Check first 10 non-empty lines for template indicators
        for (int i = 0; i < Math.min(lines.length, 15); i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // MediaNode template 4: buyer on left, seller on right
            // Detected by "MediaNode" appearing after buyer name, and "Invoice INV" format
            if (line.matches("(?i).*Invoice\s+[A-Z]+\\d+.*")) {
                // Check if MediaNode appears after this line (buyer-left layout)
                for (int j = i + 1; j < Math.min(lines.length, i + 10); j++) {
                    if (lines[j].trim().toLowerCase().contains("medianode")) {
                        log.info("Detected MediaNode buyer-left layout (Invoice INV format)");
                        return TemplateType.MEDIANODE_BUYER_LEFT;
                    }
                }
            }

            // MediaNode templates 1-3: logo first
            if (line.toLowerCase().contains("medianode")) {
                // Check if buyer info follows (client name pattern)
                for (int j = i + 1; j < Math.min(lines.length, i + 20); j++) {
                    String nextLine = lines[j].trim();
                    // If we see "invoice" or date before buyer info, it's logo-first layout
                    if (nextLine.matches("(?i).*invoice.*\\d+.*") ||
                        nextLine.matches(".*\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4}.*")) {
                        log.info("Detected MediaNode logo-first layout");
                        return TemplateType.MEDIANODE_LOGO_FIRST;
                    }
                    // If we see buyer-like info (name, address) before invoice, it's buyer-left
                    if (nextLine.matches("(?i)^[A-Z]+\\s+[A-Z]+$") || // e.g. JOHN DOE
                        nextLine.matches("(?i)^Client\\s+.*")) {
                        log.info("Detected MediaNode logo-first with buyer on left");
                        return TemplateType.MEDIANODE_LOGO_FIRST;
                    }
                }
                log.info("Detected MediaNode logo-first layout (fallback)");
                return TemplateType.MEDIANODE_LOGO_FIRST;
            }

            // FreeAgent right-aligned: invoice metadata in first line
            if (line.matches("(?i).*Invoice.*\\d+.*")) {
                return TemplateType.FREEAGENT_RIGHT_ALIGNED;
            }
        }
        return TemplateType.FREEAGENT_LEFT_ALIGNED;
    }

    private void parseMediaNodeLogoFirst(String[] lines, ExtractedInvoice.Party seller, ExtractedInvoice.Party buyer) {
        // Templates 1-3: logo first, seller info is typically "MediaNode" on right side
        // With PDFBox text extraction, seller appears early, buyer appears later

        // Find MediaNode seller block
        boolean inSellerBlock = false;
        boolean foundMediaNode = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (line.toLowerCase().contains("medianode")) {
                foundMediaNode = true;
                inSellerBlock = true;
                if (seller.getName() == null || seller.getName().isEmpty()) {
                    seller.setName("MediaNode");
                }
                continue;
            }

            if (inSellerBlock && foundMediaNode) {
                if (line.matches("(?i)^VAT[:\\s]+(.+)")) {
                    seller.setVatNumber(line.replaceFirst("(?i)^VAT[:\\s]+", "").trim());
                } else if (line.matches("(?i).*Invoice.*")) {
                    inSellerBlock = false;
                } else if (seller.getStreet() == null || seller.getStreet().isEmpty()) {
                    seller.setStreet(line);
                } else if (seller.getCity() == null || seller.getCity().isEmpty()) {
                    seller.setCity(line);
                } else if (seller.getPostcode() == null || seller.getPostcode().isEmpty()) {
                    seller.setPostcode(line);
                }
            }
        }

        // Find buyer: scan forwards, skip seller block, collect until invoice metadata
        boolean passedSeller = false;
        boolean sellerBlockEnded = false;
        List<String> buyerLines = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Start collecting after we pass MediaNode seller block
            if (line.toLowerCase().contains("medianode")) {
                passedSeller = true;
                continue;
            }

            if (passedSeller) {
                if (line.isEmpty()) {
                    sellerBlockEnded = true;
                    continue;
                }

                // Skip remaining seller lines until block ends
                if (!sellerBlockEnded) {
                    if (line.matches("(?i)^VAT[:\\s]+.+")) {
                        sellerBlockEnded = true;
                    }
                    continue;
                }

                // Now collecting buyer lines
                // Stop at invoice metadata
                if (line.matches("(?i).*Invoice\\s+[A-Z]*\\d+.*") ||
                    line.matches(".*\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4}.*") ||
                    line.matches("(?i).*Payment.*due.*") ||
                    line.matches("(?i).*Payment:.*Days.*") ||
                    line.contains("Quantity")) {
                    break;
                }
                buyerLines.add(line);
            }
        }

        // Parse collected buyer lines
        for (String line : buyerLines) {
            if (line.matches("(?i)^VAT[:\\s]+(.+)")) {
                buyer.setVatNumber(line.replaceFirst("(?i)^VAT[:\\s]+", "").trim());
                continue;
            }
            if (buyer.getName() == null || buyer.getName().isEmpty()) {
                buyer.setName(line);
            } else if (buyer.getStreet() == null || buyer.getStreet().isEmpty()) {
                buyer.setStreet(line);
            } else if (buyer.getCity() == null || buyer.getCity().isEmpty()) {
                buyer.setCity(line);
            } else if (buyer.getPostcode() == null || buyer.getPostcode().isEmpty()) {
                buyer.setPostcode(line);
            }
        }
    }

    private void parseMediaNodeBuyerLeft(String[] lines, ExtractedInvoice.Party seller, ExtractedInvoice.Party buyer) {
        // Template 4: buyer on left (first), seller (MediaNode) on right
        // In text extraction, buyer appears first, then seller

        boolean passedBuyer = false;
        boolean inSellerBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // Detect seller start
            if (line.toLowerCase().contains("medianode")) {
                passedBuyer = true;
                inSellerBlock = true;
                if (seller.getName() == null || seller.getName().isEmpty()) {
                    seller.setName("MediaNode");
                }
                continue;
            }

            if (inSellerBlock) {
                if (line.matches("(?i)^VAT[:\\s]+(.+)")) {
                    seller.setVatNumber(line.replaceFirst("(?i)^VAT[:\\s]+", "").trim());
                } else if (line.matches("(?i).*Invoice.*")) {
                    inSellerBlock = false;
                } else if (seller.getStreet() == null || seller.getStreet().isEmpty()) {
                    seller.setStreet(line);
                } else if (seller.getCity() == null || seller.getCity().isEmpty()) {
                    seller.setCity(line);
                } else if (seller.getPostcode() == null || seller.getPostcode().isEmpty()) {
                    seller.setPostcode(line);
                }
            }
        }

        // Buyer is the first non-empty block before MediaNode
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (line.toLowerCase().contains("medianode")) {
                break; // Stop at seller
            }

            // Skip invoice metadata
            if (line.matches("(?i).*Invoice.*[A-Z]+\\d+.*") ||
                line.matches(".*\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4}.*") ||
                line.matches("(?i).*Payment\\s+due.*")) {
                continue;
            }

            if (line.matches("(?i)^VAT[:\\s]+(.+)")) {
                buyer.setVatNumber(line.replaceFirst("(?i)^VAT[:\\s]+", "").trim());
                continue;
            }

            if (buyer.getName() == null || buyer.getName().isEmpty()) {
                buyer.setName(line);
            } else if (buyer.getStreet() == null || buyer.getStreet().isEmpty()) {
                buyer.setStreet(line);
            } else if (buyer.getCity() == null || buyer.getCity().isEmpty()) {
                buyer.setCity(line);
            } else if (buyer.getPostcode() == null || buyer.getPostcode().isEmpty()) {
                buyer.setPostcode(line);
            }
        }
    }

    private int parseSellerLeftAligned(String[] lines, ExtractedInvoice.Party seller) {
        int sellerIdx = 0;
        int lastPopulated = 0;
        while (sellerIdx < lines.length && sellerIdx < 6) {
            String line = lines[sellerIdx].trim();
            if (!line.isEmpty()) {
                if (line.matches("(?i)^VAT[:\\s]+(.+)")) {
                    seller.setVatNumber(line.replaceFirst("(?i)^VAT[:\\s]+", "").trim());
                } else if (seller.getName() == null || seller.getName().isEmpty()) {
                    seller.setName(line);
                } else if (seller.getStreet() == null || seller.getStreet().isEmpty()) {
                    seller.setStreet(line);
                } else if (seller.getCity() == null || seller.getCity().isEmpty()) {
                    seller.setCity(line);
                } else if (seller.getPostcode() == null || seller.getPostcode().isEmpty()) {
                    seller.setPostcode(line);
                }
                lastPopulated = sellerIdx;
            }
            sellerIdx++;
        }
        return Math.min(lastPopulated + 1, lines.length - 1);
    }

    private int parseSellerRightAligned(String[] lines, ExtractedInvoice.Party seller) {
        // Skip invoice metadata lines (Invoice #, dates, etc.)
        int startIdx = 0;
        while (startIdx < lines.length && startIdx < 6) {
            String line = lines[startIdx].trim();
            // Skip lines that look like invoice metadata
            if (line.matches("(?i).*Invoice.*\\d+.*") || 
                line.matches(".*\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4}.*") ||
                line.matches("(?i).*Payment due.*")) {
                startIdx++;
            } else {
                break;
            }
        }

        // Seller info should be after metadata
        int sellerIdx = startIdx;
        int lastPopulated = startIdx;
        while (sellerIdx < lines.length && sellerIdx < startIdx + 6) {
            String line = lines[sellerIdx].trim();
            if (!line.isEmpty() && !line.contains("INVOICE") && !line.contains("Quantity") && !line.contains("GBP")) {
                if (line.matches("(?i)^VAT[:\\s]+(.+)")) {
                    seller.setVatNumber(line.replaceFirst("(?i)^VAT[:\\s]+", "").trim());
                } else if (seller.getName() == null || seller.getName().isEmpty()) {
                    seller.setName(line);
                } else if (seller.getStreet() == null || seller.getStreet().isEmpty()) {
                    seller.setStreet(line);
                } else if (seller.getCity() == null || seller.getCity().isEmpty()) {
                    seller.setCity(line);
                } else if (seller.getPostcode() == null || seller.getPostcode().isEmpty()) {
                    seller.setPostcode(line);
                }
                lastPopulated = sellerIdx;
            }
            sellerIdx++;
        }
        return Math.min(lastPopulated + 1, lines.length - 1);
    }

    private void parseBuyerLeftAligned(String[] lines, ExtractedInvoice.Party buyer, int sellerEnd) {
        int invoiceIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].toUpperCase().contains("INVOICE")) {
                invoiceIndex = i;
                break;
            }
        }
        if (invoiceIndex == -1) {
            log.warn("Could not locate INVOICE marker in FreeAgent PDF");
            return;
        }

        for (int i = sellerEnd; i < invoiceIndex; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.matches("(?i).*Payment.*")) continue;
            if (line.matches(".*\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4}.*")) continue;
            if (line.contains("Quantity") || line.contains("Details") || line.contains("Unit Price")) break;

            if (line.matches("(?i)^VAT[:\\s]+(.+)")) {
                buyer.setVatNumber(line.replaceFirst("(?i)^VAT[:\\s]+", "").trim());
                continue;
            }

            if (buyer.getName() == null || buyer.getName().isEmpty()) {
                buyer.setName(line);
            } else if (buyer.getStreet() == null || buyer.getStreet().isEmpty()) {
                buyer.setStreet(line);
            } else if (buyer.getCity() == null || buyer.getCity().isEmpty()) {
                buyer.setCity(line);
            } else if (buyer.getPostcode() == null || buyer.getPostcode().isEmpty()) {
                buyer.setPostcode(line);
            } else if (buyer.getAdditionalStreet() == null || buyer.getAdditionalStreet().isEmpty()) {
                buyer.setAdditionalStreet(line);
            }
        }
    }

    private void parseBuyerRightAligned(String[] lines, ExtractedInvoice.Party buyer, int sellerEndInitial) {
        // In right-aligned layout, buyer is typically after seller info
        // Look for buyer after seller's address (skip seller lines)
        int sellerEnd = sellerEndInitial;
        while (sellerEnd < lines.length && sellerEnd < sellerEndInitial + 10) {
            String line = lines[sellerEnd].trim();
            if (line.isEmpty() || line.contains("Quantity") || line.contains("GBP") || 
                line.contains("Details") || line.contains("Unit Price")) {
                break;
            }
            sellerEnd++;
        }

        // Look for buyer after seller section
        int buyerStart = -1;
        for (int i = sellerEnd; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty() && !line.contains("Quantity") && !line.contains("GBP") &&
                !line.contains("Details") && !line.contains("Unit Price") &&
                !line.matches(".*\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4}.*") &&
                !line.matches("(?i).*Payment.*")) {
                buyerStart = i;
                break;
            }
        }

        if (buyerStart >= 0) {
            int i = buyerStart;
            while (i < lines.length) {
                String line = lines[i].trim();
                if (line.isEmpty() || line.contains("Quantity") || line.contains("GBP") ||
                    line.contains("Details") || line.contains("Unit Price")) {
                    break;
                }
                if (line.matches(".*\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4}.*") || line.contains("Payment due")) {
                    i++;
                    continue;
                }
                if (line.matches("(?i)^VAT[:\\s]+(.+)")) {
                    buyer.setVatNumber(line.replaceFirst("(?i)^VAT[:\\s]+", "").trim());
                } else if (buyer.getName() == null || buyer.getName().isEmpty()) {
                    buyer.setName(line);
                } else if (buyer.getStreet() == null || buyer.getStreet().isEmpty()) {
                    buyer.setStreet(line);
                } else if (buyer.getCity() == null || buyer.getCity().isEmpty()) {
                    buyer.setCity(line);
                } else if (buyer.getPostcode() == null || buyer.getPostcode().isEmpty()) {
                    buyer.setPostcode(line);
                }
                i++;
            }
        }
    }

    private LocalDate parseDate(String text) {
        // Try various date formats
        // Format: "24 April 2026"
        Matcher dm = DATE_PATTERN.matcher(text);
        if (dm.find()) {
            String raw = dm.group(1) + " " + dm.group(2) + " " + dm.group(3);
            try {
                return LocalDate.parse(raw, FREE_AGENT_DATE);
            } catch (Exception e) {
                log.warn("Could not parse date with FREE_AGENT_DATE: {}", raw);
            }
        }
        
        // Format: "24/04/2026"
        Matcher slashMatcher = DATE_PATTERN_SLASH.matcher(text);
        if (slashMatcher.find()) {
            String raw = slashMatcher.group(1) + "/" + slashMatcher.group(2) + "/" + slashMatcher.group(3);
            try {
                return LocalDate.parse(raw, SLASH_DATE);
            } catch (Exception e) {
                log.warn("Could not parse date with SLASH_DATE: {}", raw);
            }
        }
        
        // Format: "2026-04-24"
        Matcher dashMatcher = DATE_PATTERN_DASH.matcher(text);
        if (dashMatcher.find()) {
            String raw = dashMatcher.group(1) + "-" + dashMatcher.group(2) + "-" + dashMatcher.group(3);
            try {
                return LocalDate.parse(raw, DASH_DATE);
            } catch (Exception e) {
                log.warn("Could not parse date with DASH_DATE: {}", raw);
            }
        }
        
        // Try ISO format
        try {
            return LocalDate.parse(text.trim(), ISO_DATE);
        } catch (Exception e) {
            log.warn("Could not parse date with ISO_DATE: {}", text);
        }
        
        return null;
    }

    private void parseInvoiceMeta(String text, ExtractedInvoice invoice) {
        Matcher m = INVOICE_NUMBER.matcher(text);
        if (m.find()) {
            invoice.setInvoiceNumber(m.group(1));
        }

        LocalDate issueDate = parseDate(text);
        if (issueDate != null) {
            invoice.setIssueDate(issueDate);
        }

        Matcher ddm = DUE_DATE.matcher(text);
        if (ddm.find()) {
            LocalDate dueDate = parseDate(ddm.group(1));
            if (dueDate != null) {
                invoice.setDueDate(dueDate);
            }
        }

        invoice.setCurrency("GBP");
    }

    private void parseLineItems(String[] lines, ExtractedInvoice invoice) {
        List<ExtractedInvoice.LineItem> items = new ArrayList<>();
        boolean inItemSection = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.contains("Quantity") && line.contains("Details") && line.contains("Unit Price")) {
                inItemSection = true;
                continue;
            }

            if (inItemSection) {
                if (line.isEmpty()) continue;
                if (line.contains("GBP Total") || line.contains("Payment Details")) {
                    break;
                }

                // Check for discount line: "10% Discount  250.00"
                Matcher discountMatcher = DISCOUNT_LINE.matcher(line);
                if (discountMatcher.matches()) {
                    ExtractedInvoice.LineItem discountItem = new ExtractedInvoice.LineItem();
                    discountItem.setLineNumber(items.size() + 1);
                    discountItem.setQuantity(BigDecimal.ONE);
                    discountItem.setDescription(discountMatcher.group(1) + "% Discount");
                    discountItem.setUnitPrice(safeDecimal(discountMatcher.group(2)));
                    discountItem.setLineTotal(safeDecimal(discountMatcher.group(2)).negate());
                    discountItem.setVatRate(BigDecimal.ZERO);
                    discountItem.setUnitCode("EA");
                    items.add(discountItem);
                    log.info("Parsed discount line: {}% Discount = {}", discountMatcher.group(1), discountMatcher.group(2));
                    continue;
                }

                // Stop at totals section lines that aren't line items
                if (line.contains("Net Total") || line.contains("VAT") || line.contains("Total")) {
                    break;
                }

                ExtractedInvoice.LineItem item = parseLineItem(line, items.size() + 1);
                if (item != null) {
                    items.add(item);
                }
            }
        }

        invoice.setLineItems(items);
    }

    private ExtractedInvoice.LineItem parseLineItem(String line, int lineNumber) {
        try {
            Matcher richMatcher = LINE_ITEM_WITH_VAT.matcher(line);
            if (richMatcher.matches()) {
                return buildLineItem(
                        lineNumber,
                        richMatcher.group(1),
                        richMatcher.group(2),
                        richMatcher.group(3),
                        richMatcher.group(4),
                        richMatcher.group(5));
            }

            Matcher basicMatcher = LINE_ITEM.matcher(line);
            if (basicMatcher.matches()) {
                return buildLineItem(
                        lineNumber,
                        basicMatcher.group(1),
                        basicMatcher.group(2),
                        basicMatcher.group(3),
                        null,
                        basicMatcher.group(4));
            }

            // Fallback to token-based parsing to preserve older formats
            String[] parts = line.split("\\s+");
            if (parts.length < 3) return null;

            BigDecimal subtotal = null;
            BigDecimal unitPrice = null;
            BigDecimal qty = null;
            BigDecimal vatRate = BigDecimal.ZERO;
            String description = line;

            for (int k = parts.length - 1; k >= 0; k--) {
                String token = parts[k];
                if (subtotal == null) {
                    BigDecimal value = safeDecimal(token);
                    if (value != null) {
                        subtotal = value;
                        continue;
                    }
                }
                if (unitPrice == null && subtotal != null) {
                    BigDecimal value = safeDecimal(token);
                    if (value != null) {
                        unitPrice = value;
                        continue;
                    }
                }
                if (vatRate.compareTo(BigDecimal.ZERO) == 0 && token.endsWith("%")) {
                    BigDecimal value = safeDecimal(token.substring(0, token.length() - 1));
                    if (value != null) {
                        vatRate = value;
                        continue;
                    }
                }
                if (qty == null) {
                    BigDecimal value = safeDecimal(token);
                    if (value != null) {
                        qty = value;
                    }
                }
            }

            if (qty == null || unitPrice == null || subtotal == null) {
                log.warn("Failed to parse numeric values from line: {}", line);
                return null;
            }

            String qtyString = qty.stripTrailingZeros().toPlainString();
            int qtyIndex = line.indexOf(qtyString);
            if (qtyIndex >= 0) {
                String afterQty = line.substring(qtyIndex + qtyString.length()).trim();
                // remove trailing numeric tokens already parsed (unit price, VAT %, subtotal)
                description = afterQty.replaceAll("([\\u00A3£]?\\d[\\d,]*\\.\\d{2}|\\d+(?:\\.\\d+)?%?)+$", "").trim();
            }

            return buildLineItem(lineNumber, qty.toPlainString(), description, unitPrice.toPlainString(),
                    vatRate.compareTo(BigDecimal.ZERO) > 0 ? vatRate.toPlainString() : null,
                    subtotal.toPlainString());
        } catch (Exception e) {
            log.warn("Failed to parse line item: {}", line, e);
            return null;
        }
    }

    private ExtractedInvoice.LineItem buildLineItem(int lineNumber,
                                                    String qtyRaw,
                                                    String descriptionRaw,
                                                    String unitRaw,
                                                    String vatRaw,
                                                    String subtotalRaw) {
        BigDecimal quantity = safeDecimal(qtyRaw);
        BigDecimal unitPrice = safeDecimal(unitRaw);
        BigDecimal subtotal = safeDecimal(subtotalRaw);

        if (quantity == null || unitPrice == null || subtotal == null) {
            log.warn("Incomplete numeric values for line {} => qty: {}, unit: {}, subtotal: {}",
                    lineNumber, qtyRaw, unitRaw, subtotalRaw);
            return null;
        }

        BigDecimal vatRate = BigDecimal.ZERO;
        if (vatRaw != null && !vatRaw.isBlank()) {
            BigDecimal parsedVat = safeDecimal(vatRaw);
            if (parsedVat != null) {
                vatRate = parsedVat;
            }
        }

        ExtractedInvoice.LineItem item = new ExtractedInvoice.LineItem();
        item.setLineNumber(lineNumber);
        item.setQuantity(quantity);
        item.setDescription(descriptionRaw == null || descriptionRaw.isBlank()
                ? "Item " + lineNumber
                : descriptionRaw.trim());
        item.setUnitPrice(unitPrice);
        item.setLineTotal(subtotal);
        item.setVatRate(vatRate);
        item.setUnitCode(mapItemTypeToUnitCode(descriptionRaw));
        return item;
    }

    private void parseTotals(String text, ExtractedInvoice invoice) {
        // Detect EC status for VAT category determination
        String ecStatus = detectECStatus(text);
        if (ecStatus != null) {
            invoice.setEcStatus(ecStatus);
            log.info("Detected EC Status: {}", ecStatus);
        }

        // Detect payment method
        String paymentMethod = detectPaymentMethod(text);
        invoice.setPaymentMethod(paymentMethod);
        log.info("Detected Payment Method: {}", paymentMethod);

        BigDecimal netTotal = null;
        Matcher netMatcher = NET_TOTAL.matcher(text);
        if (netMatcher.find()) {
            netTotal = parseDecimal(netMatcher.group(1));
            invoice.setTotalAmount(netTotal);
            log.info("Parsed Net Total: {}", netTotal);
        }

        Matcher grandMatcher = GRAND_TOTAL.matcher(text);
        if (grandMatcher.find()) {
            BigDecimal grandTotal = parseDecimal(grandMatcher.group(1));
            invoice.setDueAmount(grandTotal);
            log.info("Parsed GBP Total: {}", grandTotal);
        }

        if (invoice.getTotalAmount() == null) {
            Matcher tm = TOTAL.matcher(text);
            if (tm.find()) {
                invoice.setTotalAmount(parseDecimal(tm.group(1)));
            } else {
                log.warn("Could not find total with standard pattern, trying alternatives");
                // Fallback: look for amount near "Total" text with £ symbol
                Pattern altTotal = Pattern.compile("(?i)Total.*?£?([\\d.,]+)");
                Matcher altTm = altTotal.matcher(text);
                if (altTm.find()) {
                    invoice.setTotalAmount(parseDecimal(altTm.group(1)));
                    log.info("Found total with alternative pattern: {}", altTm.group(1));
                } else if (invoice.getLineItems() != null && !invoice.getLineItems().isEmpty()) {
                    // Fallback: sum line item totals
                    BigDecimal lineTotal = invoice.getLineItems().stream()
                            .map(ExtractedInvoice.LineItem::getLineTotal)
                            .filter(java.util.Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (lineTotal.compareTo(BigDecimal.ZERO) > 0) {
                        invoice.setTotalAmount(lineTotal);
                        log.info("Calculated total from line items: {}", lineTotal);
                    }
                }
            }
        }

        Matcher taxMatcher = VAT_SUMMARY.matcher(text);
        BigDecimal vatAmount = BigDecimal.ZERO;
        while (taxMatcher.find()) {
            BigDecimal parsedVat = parseDecimal(taxMatcher.group(2));
            if (parsedVat != null) {
                vatAmount = vatAmount.add(parsedVat);
            }
            try {
                BigDecimal vatRate = safeDecimal(taxMatcher.group(1));
                if (vatRate != null && !vatRate.equals(BigDecimal.ZERO)) {
                    // distribute vat rate to line items lacking explicit rate
                    for (ExtractedInvoice.LineItem line : invoice.getLineItems()) {
                        if (line.getVatRate() == null || line.getVatRate().compareTo(BigDecimal.ZERO) == 0) {
                            line.setVatRate(vatRate);
                        }
                    }
                }
            } catch (Exception ignored) {
                // ignore rate parsing issues, we still captured amount
            }
        }

        // Fallback: check for VAT without rate (e.g., "VAT  962.50")
        if (vatAmount.compareTo(BigDecimal.ZERO) == 0) {
            String[] lines = text.split("\\r?\\n");
            for (String line : lines) {
                Matcher vatOnlyMatcher = VAT_AMOUNT_ONLY.matcher(line);
                if (vatOnlyMatcher.find()) {
                    vatAmount = parseDecimal(vatOnlyMatcher.group(1));
                    log.info("Parsed VAT amount (no rate): {}", vatAmount);
                    break;
                }
            }
        }

        if (vatAmount != null) {
            invoice.setVatAmount(vatAmount);
        }

        Matcher pm = PAID.matcher(text);
        if (pm.find()) {
            invoice.setPaidAmount(parseDecimal(pm.group(2)));
            try {
                invoice.getPaymentDetails().setPaymentDate(LocalDate.parse(pm.group(1), SHORT_YEAR_DATE));
            } catch (Exception e) {
                log.warn("Could not parse payment date: {}", pm.group(1));
            }
        }

        if (invoice.getDueAmount() == null) {
            Matcher dm = DUE.matcher(text);
            if (dm.find()) {
                invoice.setDueAmount(parseDecimal(dm.group(1)));
            } else if (invoice.getTotalAmount() != null) {
                // Fallback: if no due amount found, use total as due amount
                invoice.setDueAmount(invoice.getTotalAmount());
                log.info("Using total as due amount: {}", invoice.getTotalAmount());
            }
        }
    }

    private void parsePaymentDetails(String[] lines, ExtractedInvoice invoice) {
        ExtractedInvoice.PaymentDetails pd = new ExtractedInvoice.PaymentDetails();
        StringBuilder detailsText = new StringBuilder();
        boolean inPaymentBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("Payment Details")) {
                inPaymentBlock = true;
                continue;
            }
            if (inPaymentBlock && trimmed.isEmpty()) {
                break;
            }
            if (inPaymentBlock) {
                detailsText.append(trimmed).append("\n");
            }
        }

        String block = detailsText.toString();
        Matcher sm = SORT_CODE.matcher(block);
        if (sm.find()) pd.setSortCode(sm.group(1));

        Matcher am = ACCOUNT_NUMBER.matcher(block);
        if (am.find()) pd.setAccountNumber(am.group(1));

        Matcher prm = PAYMENT_REF.matcher(block);
        if (prm.find()) pd.setPaymentReference(prm.group(1).trim());

        String[] pLines = block.split("\\n");
        if (pLines.length > 0 && !pLines[0].contains("Sort Code") && !pLines[0].contains("Account")) {
            pd.setBankName(pLines[0].trim());
        }

        invoice.setPaymentDetails(pd);
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;
        return new BigDecimal(value.replace(",", ""));
    }

    private BigDecimal safeDecimal(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[£\\u00A3]", "").replace(",", "");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String detectECStatus(String text) {
        Matcher ecMatcher = EC_STATUS.matcher(text);
        if (ecMatcher.find()) {
            return ecMatcher.group(1);
        }
        return null;
    }

    private String detectPaymentMethod(String text) {
        // First check for explicit payment method declaration
        Matcher pmMatcher = PAYMENT_METHOD_TEXT.matcher(text);
        if (pmMatcher.find()) {
            String methods = pmMatcher.group(1);
            log.info("Found payment methods text: {}", methods);
            // If bank transfer or cheque is mentioned, default to credit transfer
            // Only override if alternative method is explicitly the primary
            if (BANK_TRANSFER.matcher(methods).find()) {
                return "CREDIT_TRANSFER";
            }
        }

        // Check for specific payment methods in the text
        // Priority: bank transfer/cheque > GoCardless > Stripe > PayPal
        if (GOCARDLESS.matcher(text).find()) {
            // Only use GoCardless if no bank transfer is mentioned
            if (!BANK_TRANSFER.matcher(text).find()) {
                return "GOCARDLESS";
            }
        }
        if (STRIPE.matcher(text).find()) {
            return "STRIPE";
        }
        if (PAYPAL.matcher(text).find()) {
            return "PAYPAL";
        }
        return "CREDIT_TRANSFER";
    }

    private String mapItemTypeToUnitCode(String description) {
        String descLower = description.toLowerCase();
        if (descLower.contains("hour") || descLower.contains("hr")) {
            return "HUR";
        } else if (descLower.contains("day") || descLower.contains("daily")) {
            return "DAY";
        } else if (descLower.contains("week") || descLower.contains("weekly")) {
            return "WEE";
        } else if (descLower.contains("month") || descLower.contains("monthly")) {
            return "MON";
        } else if (descLower.contains("product") || descLower.contains("item") || descLower.contains("unit")) {
            return "EA";
        }
        return "EA"; // Default to each
    }
}
