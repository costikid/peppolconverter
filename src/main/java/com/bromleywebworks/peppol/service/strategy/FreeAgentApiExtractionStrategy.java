package com.bromleywebworks.peppol.service.strategy;

import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import com.bromleywebworks.peppol.dto.freeagent.*;
import com.bromleywebworks.peppol.service.ConfigService;
import com.bromleywebworks.peppol.service.FreeAgentApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FreeAgentApiExtractionStrategy implements ExtractionStrategy {

    // Note: CustomizationID and ProfileID already hardcoded in existing MappingService.java lines 24-25
    private final FreeAgentApiClient apiClient;
    private final ConfigService configService;

    @Override
    public String getSupportedType() {
        return "freeagent-api";
    }

    public Mono<ExtractedInvoice> extractFromApi(String invoiceId) {
        return apiClient.getInvoice(invoiceId)
            .flatMap(invoice -> {
                Mono<FreeAgentContact> contactMono = apiClient.getContact(invoice.getContact());
                Mono<FreeAgentCompany> companyMono = apiClient.getCompany();
                
                return Mono.zip(contactMono, companyMono)
                    .map(tuple -> {
                        FreeAgentContact contact = tuple.getT1();
                        FreeAgentCompany company = tuple.getT2();
                        return mapToExtractedInvoice(invoice, contact, company);
                    });
            });
    }

    private ExtractedInvoice mapToExtractedInvoice(FreeAgentInvoice invoice, 
                                                   FreeAgentContact contact, 
                                                   FreeAgentCompany company) {
        ExtractedInvoice extracted = new ExtractedInvoice();
        
        // Peppol BIS 3.0 mandatory: UUID
        extracted.setUuid(UUID.randomUUID().toString());
        
        // Invoice metadata
        extracted.setInvoiceNumber(invoice.getReference());
        extracted.setIssueDate(invoice.getDatedOn());
        extracted.setDueDate(invoice.getDueOn());
        extracted.setCurrency(invoice.getCurrency());
        extracted.setTotalAmount(invoice.getNetValue());
        extracted.setPaidAmount(invoice.getPaidValue());
        extracted.setDueAmount(invoice.getDueValue());
        
        // Buyer (from contact)
        ExtractedInvoice.Party buyer = new ExtractedInvoice.Party();
        buyer.setName(contact.getOrganisationName() != null ? 
            contact.getOrganisationName() : 
            (contact.getFirstName() + " " + contact.getLastName()).trim());
        buyer.setStreet(contact.getAddress1());
        buyer.setAdditionalStreet(contact.getAddress2());
        buyer.setCity(contact.getTown());
        buyer.setPostcode(contact.getPostcode());
        buyer.setCountryCode(mapCountryCode(contact.getCountry()));
        buyer.setVatNumber(contact.getSalesTaxRegistrationNumber());
        extracted.setBuyer(buyer);
        
        // Seller (from company + config fallback)
        ExtractedInvoice.Party seller = new ExtractedInvoice.Party();
        seller.setName(company.getName());
        seller.setVatNumber(company.getSalesTaxRegistrationNumber());
        // Use config for address (company API doesn't provide it)
        seller.setStreet(configService.getSellerAddressField("street"));
        seller.setCity(configService.getSellerAddressField("city"));
        seller.setPostcode(configService.getSellerAddressField("postcode"));
        seller.setCountryCode(configService.getSellerAddressField("countryCode"));
        extracted.setSeller(seller);
        
        // Line items with UNECE unit codes
        List<ExtractedInvoice.LineItem> lineItems = invoice.getInvoiceItems().stream()
            .map(this::mapLineItem)
            .collect(Collectors.toList());
        extracted.setLineItems(lineItems);
        
        return extracted;
    }

    private ExtractedInvoice.LineItem mapLineItem(FreeAgentInvoiceItem item) {
        ExtractedInvoice.LineItem lineItem = new ExtractedInvoice.LineItem();
        lineItem.setDescription(item.getDescription());
        lineItem.setQuantity(item.getQuantity());
        lineItem.setUnitPrice(item.getPrice());
        lineItem.setLineTotal(item.getPrice().multiply(item.getQuantity()));
        lineItem.setVatRate(item.getSalesTaxRate());
        // UNECE 21 unit code
        lineItem.setUnitCode(mapUnitCode(item.getItemType()));
        return lineItem;
    }

    private String mapUnitCode(String itemType) {
        if (itemType == null) return "EA";  // Each (default)
        
        // Map FreeAgent item types to UNECE 21 unit codes
        switch (itemType.toLowerCase()) {
            case "hours":
            case "time":
                return "HUR";  // Hour
            case "days":
            case "day":
                return "DAY";  // Day
            case "months":
            case "month":
                return "MON";  // Month
            case "products":
            case "items":
                return "EA";   // Each
            default:
                return "EA";   // Default to Each
        }
    }

    private String mapCountryCode(String countryName) {
        if (countryName == null) return "GB";
        // Simple mapping - expand as needed
        if (countryName.contains("United Kingdom")) return "GB";
        if (countryName.contains("United States")) return "US";
        if (countryName.contains("Germany")) return "DE";
        if (countryName.contains("France")) return "FR";
        return "GB";  // Default
    }

    @Override
    public ExtractedInvoice extract(MultipartFile file) throws IOException {
        throw new UnsupportedOperationException("Use extractFromApi() for FreeAgent API extraction");
    }
}
