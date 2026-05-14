package com.bromleywebworks.peppol.validation;

import com.bromleywebworks.peppol.dto.ConvertRequest;
import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import com.bromleywebworks.peppol.service.MappingService;
import com.bromleywebworks.peppol.service.ValidationService;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import com.helger.ubl21.UBL21Writer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PeppolXsdValidationTest {

    @Autowired
    private MappingService mappingService;

    @Autowired
    private ValidationService validationService;

    @Test
    public void testMandatoryFields_AllPresent() {
        // Create ExtractedInvoice with all required fields
        ExtractedInvoice extracted = createTestInvoice();
        
        // Create ConvertRequest with buyer endpoint info
        ConvertRequest request = new ConvertRequest();
        request.setBuyerEndpoint("0198:buyer-test-id");
        request.setBuyerScheme("0198");
        
        // Convert to UBL
        InvoiceType invoice = mappingService.map(extracted, request);
        
        // Validate
        var errors = validationService.validate(invoice);
        assertTrue(errors.isEmpty(), "Validation errors: " + errors);
        
        // Verify UUID is set
        assertNotNull(extracted.getUuid());
        
        // Verify XML can be generated
        String xml = UBL21Writer.invoice().getAsString(invoice);
        assertNotNull(xml);
        assertTrue(xml.contains("urn:cen.eu:en16931:2017")); // CustomizationID
        assertTrue(xml.contains("urn:fdc:peppol.eu:2017:poacc:billing:01:1.0")); // ProfileID
    }

    private ExtractedInvoice createTestInvoice() {
        ExtractedInvoice extracted = new ExtractedInvoice();
        extracted.setUuid(UUID.randomUUID().toString());
        extracted.setInvoiceNumber("INV-001");
        extracted.setIssueDate(LocalDate.now());
        extracted.setDueDate(LocalDate.now().plusDays(30));
        extracted.setCurrency("GBP");
        extracted.setTotalAmount(new BigDecimal("100.00"));
        extracted.setVatAmount(new BigDecimal("20.00"));
        extracted.setPaidAmount(BigDecimal.ZERO);
        extracted.setDueAmount(new BigDecimal("120.00"));

        ExtractedInvoice.Party seller = new ExtractedInvoice.Party();
        seller.setName("Test Seller Ltd");
        seller.setStreet("123 Test Street");
        seller.setCity("London");
        seller.setPostcode("SW1A 1AA");
        seller.setCountryCode("GB");
        seller.setVatNumber("GB123456789");
        extracted.setSeller(seller);

        ExtractedInvoice.Party buyer = new ExtractedInvoice.Party();
        buyer.setName("Test Buyer Ltd");
        buyer.setStreet("456 Buyer Road");
        buyer.setCity("Manchester");
        buyer.setPostcode("M1 1AA");
        buyer.setCountryCode("GB");
        buyer.setVatNumber("GB987654321");
        extracted.setBuyer(buyer);

        ExtractedInvoice.LineItem item = new ExtractedInvoice.LineItem();
        item.setLineNumber(1);
        item.setDescription("Test Item");
        item.setQuantity(new BigDecimal("1"));
        item.setUnitCode("EA");
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setLineTotal(new BigDecimal("100.00"));
        item.setVatRate(new BigDecimal("20"));
        extracted.setLineItems(Collections.singletonList(item));

        return extracted;
    }
}
