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
public class QuickBooksExtractionStrategy implements ExtractionStrategy {

    private static final Pattern INVOICE_NUMBER   = Pattern.compile("INVOICE\\s+(\\d+)");
    // Generic date patterns - don't require specific labels
    private static final Pattern DATE_SLASH        = Pattern.compile("\\b(\\d{2}/\\d{2}/\\d{4})\\b");
    private static final Pattern DATE_DASH        = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern DATE_TEXT        = Pattern.compile("\\b(\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4})\\b");
    // Due date patterns with and without labels
    private static final Pattern DUE_DATE_SLASH    = Pattern.compile("(?:DUE\\s+DATE\\s*)?(\\d{2}/\\d{2}/\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DUE_DATE_DASH    = Pattern.compile("(?:DUE\\s+DATE\\s*)?(\\d{4}-\\d{2}-\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DUE_DATE_TEXT    = Pattern.compile("(?:DUE\\s+DATE\\s*)?(\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern TERMS             = Pattern.compile("TERMS\\s+Net\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VAT_REG           = Pattern.compile("VAT\\s+Registration\\s+No\\.?[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE);
    // More flexible totals patterns
    private static final Pattern SUBTOTAL          = Pattern.compile("(?:SUBTOTAL|Subtotal|subtotal)\\s*[:\\s]*([\\d.,]+)");
    private static final Pattern VAT_TOTAL         = Pattern.compile("(?:VAT\\s+TOTAL|VAT|Tax|TOTAL)\\s*[:\\s]*([\\d.,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BALANCE_DUE       = Pattern.compile("(?:BALANCE\\s+DUE|Due|Total|AMOUNT)\\s*(?:GBP|£|\\$)?\\s*[:\\s]*([\\d.,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BANK_DETAILS      = Pattern.compile("Bank:\\s+(.+?)\\s+-\\s+Account\\s+No\\.\\s+(\\d+)\\s+-\\s+Sort\\s+Code:\\s+([\\d-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VAT_PERCENT_TOKEN = Pattern.compile("^(\\d+)%$");

    private static final DateTimeFormatter QB_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter QB_DATE_ALT = new DateTimeFormatterBuilder()
            .appendPattern("d MMMM yyyy")
            .toFormatter();
    private static final DateTimeFormatter QB_DATE_ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final long PDF_MAX_MAIN_MEMORY  = 20 * 1024 * 1024L;

    @Override
    public String getSupportedType() {
        return "quickbooks";
    }

    @Override
    public ExtractedInvoice extract(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream(),
                MemoryUsageSetting.setupMixed(PDF_MAX_MAIN_MEMORY))) {
            log.info("QuickBooks PDF loaded, pages: {}", document.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String fullText = stripper.getText(document);
            log.info("QuickBooks extracted text length: {} chars", fullText.length());
            log.debug("QuickBooks extracted PDF text:\n{}", fullText);
            return parseQuickBooksText(fullText);
        } catch (Exception e) {
            log.error("Error extracting QuickBooks PDF: {}", e.getMessage(), e);
            throw e;
        }
    }

    private ExtractedInvoice parseQuickBooksText(String text) {
        ExtractedInvoice invoice = new ExtractedInvoice();
        invoice.setPaymentDetails(new ExtractedInvoice.PaymentDetails());
        String[] lines = text.split("\\r?\\n");

        parseInvoiceMeta(text, invoice);
        parseSellerAndBuyer(lines, invoice);
        parseLineItems(lines, invoice);
        parseTotals(text, invoice);
        parsePaymentDetails(text, invoice);

        // Validate critical fields
        validateExtractedInvoice(invoice);

        log.info("Parsed QuickBooks invoice: number={}, seller={}, buyer={}",
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
        if (invoice.getLineItems() == null || invoice.getLineItems().isEmpty()) {
            log.warn("No line items extracted");
        }
        if (invoice.getTotalAmount() == null || invoice.getTotalAmount().compareTo(BigDecimal.ZERO) == 0) {
            log.warn("Missing or zero total amount");
        }
    }

    private LocalDate parseQuickBooksDate(String text) {
        // Try dd/MM/yyyy format (generic, no label required)
        Matcher slashMatcher = DATE_SLASH.matcher(text);
        if (slashMatcher.find()) {
            try {
                return LocalDate.parse(slashMatcher.group(1), QB_DATE);
            } catch (Exception e) {
                log.warn("Could not parse date with QB_DATE: {}", slashMatcher.group(1));
            }
        }
        
        // Try yyyy-MM-dd format (generic, no label required)
        Matcher dashMatcher = DATE_DASH.matcher(text);
        if (dashMatcher.find()) {
            try {
                return LocalDate.parse(dashMatcher.group(1), QB_DATE_ISO);
            } catch (Exception e) {
                log.warn("Could not parse date with QB_DATE_ISO: {}", dashMatcher.group(1));
            }
        }
        
        // Try d MMMM yyyy format (generic, no label required)
        Matcher textMatcher = DATE_TEXT.matcher(text);
        if (textMatcher.find()) {
            try {
                return LocalDate.parse(textMatcher.group(1), QB_DATE_ALT);
            } catch (Exception e) {
                log.warn("Could not parse date with QB_DATE_ALT: {}", textMatcher.group(1));
            }
        }
        
        return null;
    }

    private LocalDate parseQuickBooksDueDate(String text) {
        // Try dd/MM/yyyy format (with or without DUE DATE label)
        Matcher slashMatcher = DUE_DATE_SLASH.matcher(text);
        if (slashMatcher.find()) {
            try {
                return LocalDate.parse(slashMatcher.group(1), QB_DATE);
            } catch (Exception e) {
                log.warn("Could not parse due date with QB_DATE: {}", slashMatcher.group(1));
            }
        }
        
        // Try yyyy-MM-dd format (with or without DUE DATE label)
        Matcher dashMatcher = DUE_DATE_DASH.matcher(text);
        if (dashMatcher.find()) {
            try {
                return LocalDate.parse(dashMatcher.group(1), QB_DATE_ISO);
            } catch (Exception e) {
                log.warn("Could not parse due date with QB_DATE_ISO: {}", dashMatcher.group(1));
            }
        }
        
        // Try d MMMM yyyy format (with or without DUE DATE label)
        Matcher textMatcher = DUE_DATE_TEXT.matcher(text);
        if (textMatcher.find()) {
            try {
                return LocalDate.parse(textMatcher.group(1), QB_DATE_ALT);
            } catch (Exception e) {
                log.warn("Could not parse due date with QB_DATE_ALT: {}", textMatcher.group(1));
            }
        }
        
        return null;
    }

    private void parseInvoiceMeta(String text, ExtractedInvoice invoice) {
        Matcher m = INVOICE_NUMBER.matcher(text);
        if (m.find()) invoice.setInvoiceNumber(m.group(1));

        LocalDate issueDate = parseQuickBooksDate(text);
        if (issueDate != null) {
            invoice.setIssueDate(issueDate);
        }

        LocalDate dueDate = parseQuickBooksDueDate(text);
        if (dueDate != null) {
            invoice.setDueDate(dueDate);
        }

        if (invoice.getDueDate() == null && invoice.getIssueDate() != null) {
            Matcher tm = TERMS.matcher(text);
            if (tm.find()) {
                try {
                    int days = Integer.parseInt(tm.group(1));
                    invoice.setDueDate(invoice.getIssueDate().plusDays(days));
                    log.info("Calculated QB due date from terms (Net {}): {}", days, invoice.getDueDate());
                } catch (Exception e) {
                    log.warn("Could not calculate due date from terms");
                }
            }
        }

        invoice.setCurrency("GBP");
    }

    private void parseSellerAndBuyer(String[] lines, ExtractedInvoice invoice) {
        ExtractedInvoice.Party seller = new ExtractedInvoice.Party();
        ExtractedInvoice.Party buyer  = new ExtractedInvoice.Party();

        int sellerEnd = lines.length;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.equalsIgnoreCase("VAT Invoice") || line.contains("INVOICE TO")) {
                sellerEnd = i;
                break;
            }
        }

        for (int i = 0; i < sellerEnd; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.contains("@") || line.startsWith("http")) continue;
            Matcher vm = VAT_REG.matcher(line);
            if (vm.find()) {
                seller.setVatNumber(vm.group(1));
                continue;
            }
            if (seller.getName() == null || seller.getName().isEmpty()) {
                seller.setName(line);
            } else if (seller.getStreet() == null || seller.getStreet().isEmpty()) {
                seller.setStreet(line);
            } else if (seller.getCity() == null || seller.getCity().isEmpty()) {
                seller.setCity(line);
            } else if (seller.getPostcode() == null || seller.getPostcode().isEmpty()) {
                seller.setPostcode(line);
            }
        }
        seller.setCountryCode("GB");

        int buyerStart = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("INVOICE TO")) {
                buyerStart = i + 1;
                break;
            }
        }

        if (buyerStart >= 0) {
            for (int i = buyerStart; i < lines.length; i++) {
                String raw  = lines[i].trim();
                if (raw.isEmpty()) continue;
                if (raw.contains("DESCRIPTION") || (raw.contains("DATE") && raw.contains("QTY"))) break;

                String line = stripInvoiceMetadata(raw);
                if (line.isEmpty()) continue;

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
        buyer.setCountryCode("GB");

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

    private String stripInvoiceMetadata(String line) {
        return line
                .replaceAll("INVOICE\\s+\\d+", "")
                .replaceAll("DUE DATE\\s+\\d{2}/\\d{2}/\\d{4}", "")
                .replaceAll("\\bDATE\\s+\\d{2}/\\d{2}/\\d{4}", "")
                .replaceAll("TERMS\\s+Net\\s+\\d+", "")
                .replaceAll("\\d{2}/\\d{2}/\\d{4}", "")
                .replaceAll("Net\\s+\\d+", "")
                .trim();
    }

    private void parseLineItems(String[] lines, ExtractedInvoice invoice) {
        List<ExtractedInvoice.LineItem> items = new ArrayList<>();

        boolean inTable = false;
        ExtractedInvoice.LineItem pendingItem = null;

        for (String rawLine : lines) {
            String line = rawLine.trim();

            if (!inTable) {
                if (line.contains("DESCRIPTION") && (line.contains("RATE") || line.contains("AMOUNT"))) {
                    inTable = true;
                }
                continue;
            }

            if (line.matches("(?i)SUBTOTAL.*") || line.matches("(?i)TOTAL.*") || line.matches("(?i)BALANCE DUE.*")) {
                if (pendingItem != null) {
                    items.add(pendingItem);
                    pendingItem = null;
                }
                break;
            }

            if (line.isEmpty()) continue;

            ExtractedInvoice.LineItem parsed = tryParseLineItem(line, items.size() + 1);
            if (parsed != null) {
                if (pendingItem != null) items.add(pendingItem);
                pendingItem = parsed;
            } else if (pendingItem != null) {
                pendingItem.setDescription(pendingItem.getDescription() + " " + line);
            }
        }

        if (pendingItem != null) items.add(pendingItem);

        invoice.setLineItems(items);
    }

    private ExtractedInvoice.LineItem tryParseLineItem(String line, int lineNumber) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 3) return null;

        int idx = parts.length - 1;

        BigDecimal amount = tryDecimal(parts[idx]);
        if (amount == null) return null;
        idx--;

        BigDecimal rate = tryDecimal(parts[idx]);
        if (rate == null) return null;
        idx--;

        BigDecimal qty = tryDecimal(parts[idx]);
        if (qty == null) return null;
        idx--;

        BigDecimal vatRate = BigDecimal.ZERO;
        if (idx >= 0) {
            Matcher vm = VAT_PERCENT_TOKEN.matcher(parts[idx]);
            if (vm.matches()) {
                try { vatRate = new BigDecimal(vm.group(1)); } catch (Exception ignored) {}
                idx--;
            }
        }

        String description = idx >= 0
                ? String.join(" ", java.util.Arrays.copyOfRange(parts, 0, idx + 1)).trim()
                : "";
        description = description.replaceAll("^\\d{2}/\\d{2}/\\d{4}\\s*", "").trim();

        if (description.isEmpty() && idx < 0) return null;

        ExtractedInvoice.LineItem item = new ExtractedInvoice.LineItem();
        item.setLineNumber(lineNumber);
        item.setQuantity(qty);
        item.setDescription(description.isEmpty() ? "Item " + lineNumber : description);
        item.setUnitPrice(rate);
        item.setLineTotal(amount);
        item.setVatRate(vatRate);
        item.setUnitCode("EA");
        return item;
    }

    private BigDecimal tryDecimal(String s) {
        if (s == null) return null;
        try {
            return new BigDecimal(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void parseTotals(String text, ExtractedInvoice invoice) {
        Matcher sm = SUBTOTAL.matcher(text);
        if (sm.find()) {
            invoice.setTotalAmount(parseDecimal(sm.group(1)));
        } else {
            log.warn("Could not find SUBTOTAL in QuickBooks invoice");
            // Fallback: try to calculate from line items
            BigDecimal lineTotal = invoice.getLineItems().stream()
                .map(ExtractedInvoice.LineItem::getLineTotal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (lineTotal.compareTo(BigDecimal.ZERO) > 0) {
                invoice.setTotalAmount(lineTotal);
                log.info("Calculated total from line items: {}", lineTotal);
            }
        }

        Matcher vm = VAT_TOTAL.matcher(text);
        if (vm.find()) {
            invoice.setVatAmount(parseDecimal(vm.group(1)));
        } else {
            log.warn("Could not find VAT TOTAL in QuickBooks invoice");
        }

        Matcher bdm = BALANCE_DUE.matcher(text);
        if (bdm.find()) {
            invoice.setDueAmount(parseDecimal(bdm.group(1)));
        } else {
            log.warn("Could not find BALANCE DUE in QuickBooks invoice");
            // Fallback: use total + VAT as due amount
            if (invoice.getTotalAmount() != null) {
                BigDecimal vat = invoice.getVatAmount() != null ? invoice.getVatAmount() : BigDecimal.ZERO;
                invoice.setDueAmount(invoice.getTotalAmount().add(vat));
                log.info("Calculated due amount from total + VAT: {}", invoice.getDueAmount());
            }
        }

        // Fallback: derive vatAmount from balanceDue - subtotal
        if ((invoice.getVatAmount() == null || invoice.getVatAmount().compareTo(BigDecimal.ZERO) == 0)
                && invoice.getDueAmount() != null && invoice.getTotalAmount() != null
                && invoice.getDueAmount().compareTo(invoice.getTotalAmount()) > 0) {
            BigDecimal derived = invoice.getDueAmount().subtract(invoice.getTotalAmount());
            invoice.setVatAmount(derived);
            log.info("Derived QB vatAmount from balanceDue - subtotal: {}", derived);
        }

        log.info("QB totals — subtotal: {}, vatAmount: {}, balanceDue: {}",
                invoice.getTotalAmount(), invoice.getVatAmount(), invoice.getDueAmount());
    }

    private void parsePaymentDetails(String text, ExtractedInvoice invoice) {
        ExtractedInvoice.PaymentDetails pd = new ExtractedInvoice.PaymentDetails();

        Matcher bm = BANK_DETAILS.matcher(text);
        if (bm.find()) {
            pd.setBankName(bm.group(1).trim());
            pd.setAccountNumber(bm.group(2));
            pd.setSortCode(bm.group(3).replace("-", ""));
            log.info("Parsed QB payment details: bank={}, account={}, sort={}", pd.getBankName(), pd.getAccountNumber(), pd.getSortCode());
        }

        invoice.setPaymentDetails(pd);
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;
        return new BigDecimal(value.replace(",", ""));
    }
}
