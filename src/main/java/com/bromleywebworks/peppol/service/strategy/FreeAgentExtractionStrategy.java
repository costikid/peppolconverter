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

    private static final Pattern INVOICE_NUMBER = Pattern.compile("INVOICE\\s+(\\d+)");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2})\\s+([A-Za-z]+)\\s+(\\d{4})");
    private static final Pattern DUE_DATE = Pattern.compile("Payment due by\\s+(\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4})");
    private static final Pattern LINE_ITEM = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s+(.+?)\\s+([\\d.,]+)\\s+([\\d.,]+)$");
    private static final Pattern TOTAL = Pattern.compile("GBP Total\\s+£?([\\d.,]+)");
    private static final Pattern PAID = Pattern.compile("Payment:\\s*(\\d{1,2}\\s+[A-Za-z]+\\s+\\d{2,4})\\s+£?([\\d.,]+)");
    private static final Pattern DUE = Pattern.compile("GBP Due\\s+£?([\\d.,]+)");
    private static final Pattern SORT_CODE = Pattern.compile("Sort Code:\\s*(\\d{6})");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("Account Number:\\s*(\\d+)");
    private static final Pattern PAYMENT_REF = Pattern.compile("Payment Reference:\\s*(.+)");
    private static final Pattern BANK_NAME = Pattern.compile("^([A-Za-z]+)\\s*$");

    private static final DateTimeFormatter FREE_AGENT_DATE = new DateTimeFormatterBuilder()
            .appendPattern("d MMMM yyyy")
            .toFormatter();

    private static final DateTimeFormatter SHORT_YEAR_DATE = new DateTimeFormatterBuilder()
            .appendPattern("d MMMM ")
            .appendValueReduced(ChronoField.YEAR, 2, 4, 2000)
            .toFormatter();

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

        log.info("Parsed FreeAgent invoice: number={}, seller={}, buyer={}",
                invoice.getInvoiceNumber(),
                invoice.getSeller() != null ? invoice.getSeller().getName() : "null",
                invoice.getBuyer() != null ? invoice.getBuyer().getName() : "null");

        return invoice;
    }

    private void parseSellerAndBuyer(String[] lines, ExtractedInvoice invoice) {
        ExtractedInvoice.Party seller = new ExtractedInvoice.Party();
        ExtractedInvoice.Party buyer = new ExtractedInvoice.Party();

        int sellerIdx = 0;
        while (sellerIdx < lines.length && sellerIdx < 4) {
            String line = lines[sellerIdx].trim();
            if (!line.isEmpty()) {
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
            sellerIdx++;
        }

        int buyerStart = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.contains("INVOICE")) {
                for (int j = i + 1; j < lines.length; j++) {
                    String nextLine = lines[j].trim();
                    if (!nextLine.isEmpty() && !nextLine.matches(".*\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4}.*")) {
                        buyerStart = j;
                        break;
                    }
                }
                break;
            }
        }

        if (buyerStart >= 0) {
            int i = buyerStart;
            while (i < lines.length) {
                String line = lines[i].trim();
                if (line.isEmpty() || line.contains("Quantity") || line.contains("GBP")) {
                    break;
                }
                if (line.matches(".*\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4}.*") || line.contains("Payment due")) {
                    i++;
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
                i++;
            }
        }

        if (seller.getCountryCode() == null || seller.getCountryCode().isEmpty()) {
            seller.setCountryCode("GB");
        }
        if (buyer.getCountryCode() == null || buyer.getCountryCode().isEmpty()) {
            buyer.setCountryCode("GB");
        }

        invoice.setSeller(seller);
        invoice.setBuyer(buyer);
    }

    private void parseInvoiceMeta(String text, ExtractedInvoice invoice) {
        Matcher m = INVOICE_NUMBER.matcher(text);
        if (m.find()) {
            invoice.setInvoiceNumber(m.group(1));
        }

        Matcher dm = DATE_PATTERN.matcher(text);
        if (dm.find()) {
            String raw = dm.group(1) + " " + dm.group(2) + " " + dm.group(3);
            try {
                invoice.setIssueDate(LocalDate.parse(raw, FREE_AGENT_DATE));
            } catch (Exception e) {
                log.warn("Could not parse issue date: {}", raw);
            }
        }

        Matcher ddm = DUE_DATE.matcher(text);
        if (ddm.find()) {
            try {
                invoice.setDueDate(LocalDate.parse(ddm.group(1), FREE_AGENT_DATE));
            } catch (Exception e) {
                log.warn("Could not parse due date: {}", ddm.group(1));
            }
        }

        invoice.setCurrency("GBP");
    }

    private void parseLineItems(String[] lines, ExtractedInvoice invoice) {
        List<ExtractedInvoice.LineItem> items = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.contains("Quantity") && line.contains("Details") && line.contains("Unit Price")) {
                for (int j = i + 1; j < lines.length; j++) {
                    String itemLine = lines[j].trim();
                    if (itemLine.isEmpty() || itemLine.contains("Total") || itemLine.contains("GBP") || itemLine.contains("Payment")) {
                        break;
                    }
                    ExtractedInvoice.LineItem item = parseLineItem(itemLine, items.size() + 1);
                    if (item != null) {
                        items.add(item);
                    }
                }
                break;
            }
        }

        invoice.setLineItems(items);
    }

    private ExtractedInvoice.LineItem parseLineItem(String line, int lineNumber) {
        try {
            String[] parts = line.split("\\s+");
            if (parts.length < 4) return null;

            BigDecimal qty = null;
            BigDecimal unitPrice = null;
            BigDecimal subtotal = null;

            for (int k = parts.length - 1; k >= 0; k--) {
                try {
                    if (subtotal == null && parts[k].matches("\\d+\\.\\d{2}")) {
                        subtotal = new BigDecimal(parts[k]);
                    } else if (unitPrice == null && parts[k].matches("\\d+\\.\\d{2}") && subtotal != null) {
                        unitPrice = new BigDecimal(parts[k]);
                    } else if (qty == null && parts[k].matches("\\d+")) {
                        qty = new BigDecimal(parts[k]);
                        break;
                    }
                } catch (NumberFormatException e) {
                    // Continue
                }
            }

            if (qty == null || unitPrice == null || subtotal == null) {
                log.warn("Failed to parse numeric values from line: {}", line);
                return null;
            }

            int qtyIdx = -1;
            for (int k = 0; k < parts.length; k++) {
                if (parts[k].equals(qty.toString())) {
                    qtyIdx = k;
                    break;
                }
            }

            String description = "";
            if (qtyIdx >= 0 && qtyIdx < parts.length - 2) {
                description = String.join(" ", java.util.Arrays.copyOfRange(parts, qtyIdx + 1, parts.length - 2));
            }

            ExtractedInvoice.LineItem item = new ExtractedInvoice.LineItem();
            item.setLineNumber(lineNumber);
            item.setQuantity(qty);
            item.setDescription(description);
            item.setUnitPrice(unitPrice);
            item.setLineTotal(subtotal);
            item.setVatRate(BigDecimal.ZERO);
            item.setUnitCode("EA");

            return item;
        } catch (Exception e) {
            log.warn("Failed to parse line item: {}", line, e);
            return null;
        }
    }

    private void parseTotals(String text, ExtractedInvoice invoice) {
        Matcher tm = TOTAL.matcher(text);
        if (tm.find()) {
            invoice.setTotalAmount(parseDecimal(tm.group(1)));
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

        Matcher dm = DUE.matcher(text);
        if (dm.find()) {
            invoice.setDueAmount(parseDecimal(dm.group(1)));
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
}
