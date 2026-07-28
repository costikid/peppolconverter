package com.bromleywebworks.peppol.service;

import com.bromleywebworks.peppol.dto.ConvertRequest;
import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentCompany;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentContact;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentInvoice;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentInvoiceItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FreeAgentInvoiceMapper {

    private final ConfigService configService;

    public ExtractedInvoice mapToExtractedInvoice(FreeAgentInvoice faInvoice,
                                                   FreeAgentContact faContact,
                                                   FreeAgentCompany faCompany) {
        ExtractedInvoice invoice = new ExtractedInvoice();
        invoice.setPaymentDetails(new ExtractedInvoice.PaymentDetails());

        invoice.setInvoiceNumber(faInvoice.getReference());
        invoice.setIssueDate(parseDate(faInvoice.getDatedOn()));
        invoice.setDueDate(parseDate(faInvoice.getDueOn()));
        invoice.setCurrency(faInvoice.getCurrency());
        invoice.setTotalAmount(parseDecimal(faInvoice.getNetValue()));
        invoice.setVatAmount(parseDecimal(faInvoice.getSalesTaxValue()));
        invoice.setDueAmount(parseDecimal(faInvoice.getDueValue()));
        invoice.setPaidAmount(parseDecimal(faInvoice.getPaidValue()));
        invoice.setEcStatus(faInvoice.getEcStatus());

        if (faInvoice.getPaymentTermsInDays() != null) {
            invoice.setPaymentMethod("CREDIT_TRANSFER");
        }

        ExtractedInvoice.Party seller = new ExtractedInvoice.Party();
        if (faCompany != null) {
            seller.setName(faCompany.getName());
            seller.setVatNumber(faCompany.getSalesTaxRegistrationNumber());
        }
        invoice.setSeller(seller);

        ExtractedInvoice.Party buyer = new ExtractedInvoice.Party();
        if (faContact != null) {
            String buyerName = faContact.getOrganisationName() != null && !faContact.getOrganisationName().isEmpty()
                    ? faContact.getOrganisationName()
                    : trimJoin(faContact.getFirstName(), faContact.getLastName());
            buyer.setName(buyerName);
            buyer.setCompanyName(buyerName);
            buyer.setStreet(faContact.getAddress1());
            buyer.setAdditionalStreet(faContact.getAddress2());
            buyer.setCity(faContact.getTown());
            buyer.setPostcode(faContact.getPostcode());
            buyer.setCountryCode(mapCountryCode(faContact.getCountry()));
            buyer.setVatNumber(faContact.getSalesTaxRegistrationNumber());
        }
        invoice.setBuyer(buyer);

        if (faInvoice.getInvoiceItems() != null) {
            Map<String, String> unitMappings = configService.getUnitMappings();
            if (invoice.getLineItems() == null) {
                invoice.setLineItems(new java.util.ArrayList<>());
            }
            int lineNumber = 1;
            for (FreeAgentInvoiceItem item : faInvoice.getInvoiceItems()) {
                ExtractedInvoice.LineItem line = new ExtractedInvoice.LineItem();
                line.setLineNumber(item.getPosition() != null ? item.getPosition() : lineNumber);
                line.setDescription(item.getDescription());
                line.setQuantity(parseDecimal(item.getQuantity()));
                line.setUnitCode(resolveUnitCode(item.getItemType(), unitMappings));
                line.setUnitPrice(parseDecimal(item.getPrice()));
                BigDecimal qty = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
                BigDecimal price = line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO;
                line.setLineTotal(qty.multiply(price));
                line.setVatRate(parseDecimal(item.getSalesTaxRate()));
                invoice.getLineItems().add(line);
                lineNumber++;
            }
        }

        ExtractedInvoice.PaymentDetails pd = invoice.getPaymentDetails();
        pd.setBankName(configService.getSellerBankField("bankName"));
        pd.setSortCode(configService.getSellerBankField("sortCode"));
        pd.setAccountNumber(configService.getSellerBankField("accountNumber"));
        pd.setPaymentReference(faInvoice.getReference());

        log.info("Mapped FreeAgent invoice: number={}, seller={}, buyer={}, lineItems={}",
                invoice.getInvoiceNumber(),
                invoice.getSeller() != null ? invoice.getSeller().getName() : "null",
                invoice.getBuyer() != null ? invoice.getBuyer().getName() : "null",
                invoice.getLineItems().size());

        return invoice;
    }

    public ConvertRequest buildConvertRequest(ExtractedInvoice extracted) {
        ConvertRequest request = new ConvertRequest();
        request.setCurrency(extracted.getCurrency());
        if (extracted.getDueDate() != null) {
            request.setDueDate(extracted.getDueDate().toString());
        }

        String buyerName = extracted.getBuyer() != null ? extracted.getBuyer().getCompanyName() : null;
        if (buyerName == null && extracted.getBuyer() != null) {
            buyerName = extracted.getBuyer().getName();
        }
        if (buyerName != null) {
            var lookup = configService.getBuyerLookup(buyerName);
            if (lookup != null) {
                if (lookup.has("endpointID")) {
                    request.setBuyerEndpoint(lookup.get("endpointID").asText());
                }
                if (lookup.has("schemeID")) {
                    request.setBuyerScheme(lookup.get("schemeID").asText());
                }
            }
        }

        return request;
    }

    private String resolveUnitCode(String itemType, Map<String, String> unitMappings) {
        if (itemType == null) {
            return "EA";
        }
        String lower = itemType.toLowerCase();
        if (unitMappings != null && unitMappings.containsKey(lower)) {
            return unitMappings.get(lower);
        }
        return switch (lower) {
            case "hours" -> "HUR";
            case "days" -> "DAY";
            default -> "EA";
        };
    }

    private String mapCountryCode(String countryName) {
        if (countryName == null || countryName.isEmpty()) {
            return null;
        }
        return switch (countryName.toLowerCase()) {
            case "united kingdom", "uk", "england", "scotland", "wales", "northern ireland" -> "GB";
            case "ireland", "republic of ireland" -> "IE";
            case "united states", "usa", "us" -> "US";
            case "france" -> "FR";
            case "germany" -> "DE";
            case "spain" -> "ES";
            case "italy" -> "IT";
            case "netherlands" -> "NL";
            case "belgium" -> "BE";
            case "portugal" -> "PT";
            case "sweden" -> "SE";
            case "denmark" -> "DK";
            case "finland" -> "FI";
            case "norway" -> "NO";
            case "poland" -> "PL";
            case "australia" -> "AU";
            case "canada" -> "CA";
            case "new zealand" -> "NZ";
            default -> null;
        };
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.length() >= 10 ? dateStr.substring(0, 10) : dateStr);
        } catch (Exception e) {
            log.warn("Could not parse date: {}", dateStr);
            return null;
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            log.warn("Could not parse decimal: {}", value);
            return BigDecimal.ZERO;
        }
    }

    private String trimJoin(String a, String b) {
        StringBuilder sb = new StringBuilder();
        if (a != null && !a.isEmpty()) sb.append(a);
        if (b != null && !b.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(b);
        }
        return sb.toString();
    }
}
