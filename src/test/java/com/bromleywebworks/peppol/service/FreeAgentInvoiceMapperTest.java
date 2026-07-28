package com.bromleywebworks.peppol.service;

import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentCompany;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentContact;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentInvoice;
import com.bromleywebworks.peppol.dto.freeagent.FreeAgentInvoiceItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FreeAgentInvoiceMapperTest {

    private ConfigService configService;
    private FreeAgentInvoiceMapper mapper;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        when(configService.getUnitMappings()).thenReturn(java.util.Map.of("hours", "HUR", "day", "DAY"));
        when(configService.getSellerBankField("bankName")).thenReturn("Test Bank");
        when(configService.getSellerBankField("sortCode")).thenReturn("123456");
        when(configService.getSellerBankField("accountNumber")).thenReturn("12345678");
        when(configService.getBuyerLookup(anyString())).thenReturn(null);

        mapper = new FreeAgentInvoiceMapper(configService);
    }

    @Test
    void mapToExtractedInvoice_populatesAllFields() {
        FreeAgentInvoice faInvoice = new FreeAgentInvoice();
        faInvoice.setReference("INV-001");
        faInvoice.setDatedOn("2024-01-15");
        faInvoice.setDueOn("2024-02-14");
        faInvoice.setCurrency("GBP");
        faInvoice.setNetValue("1000.00");
        faInvoice.setSalesTaxValue("200.00");
        faInvoice.setTotalValue("1200.00");
        faInvoice.setPaidValue("0.0");
        faInvoice.setDueValue("1200.00");
        faInvoice.setEcStatus("EC Goods");
        faInvoice.setPaymentTermsInDays(30);
        faInvoice.setContact("https://api.freeagent.com/v2/contacts/2");

        FreeAgentInvoiceItem item1 = new FreeAgentInvoiceItem();
        item1.setPosition(1);
        item1.setDescription("Consulting services");
        item1.setItemType("Hours");
        item1.setPrice("100.0");
        item1.setQuantity("10.0");
        item1.setSalesTaxRate("20");

        FreeAgentInvoiceItem item2 = new FreeAgentInvoiceItem();
        item2.setPosition(2);
        item2.setDescription("Software license");
        item2.setItemType("Units");
        item2.setPrice("50.0");
        item2.setQuantity("5.0");
        item2.setSalesTaxRate("20");

        faInvoice.setInvoiceItems(List.of(item1, item2));

        FreeAgentContact faContact = new FreeAgentContact();
        faContact.setOrganisationName("Acme Ltd");
        faContact.setAddress1("11 George Street");
        faContact.setAddress2("South Court");
        faContact.setTown("London");
        faContact.setPostcode("SE1 6HA");
        faContact.setCountry("United Kingdom");
        faContact.setSalesTaxRegistrationNumber("GB123456789");

        FreeAgentCompany faCompany = new FreeAgentCompany();
        faCompany.setName("My Company Ltd");
        faCompany.setSalesTaxRegistrationNumber("GB987654321");
        faCompany.setCurrency("GBP");

        ExtractedInvoice result = mapper.mapToExtractedInvoice(faInvoice, faContact, faCompany);

        assertEquals("INV-001", result.getInvoiceNumber());
        assertEquals(LocalDate.of(2024, 1, 15), result.getIssueDate());
        assertEquals(LocalDate.of(2024, 2, 14), result.getDueDate());
        assertEquals("GBP", result.getCurrency());
        assertEquals(new BigDecimal("1000.00"), result.getTotalAmount());
        assertEquals(new BigDecimal("200.00"), result.getVatAmount());
        assertEquals(new BigDecimal("1200.00"), result.getDueAmount());
        assertEquals("EC Goods", result.getEcStatus());

        assertNotNull(result.getSeller());
        assertEquals("My Company Ltd", result.getSeller().getName());
        assertEquals("GB987654321", result.getSeller().getVatNumber());

        assertNotNull(result.getBuyer());
        assertEquals("Acme Ltd", result.getBuyer().getName());
        assertEquals("Acme Ltd", result.getBuyer().getCompanyName());
        assertEquals("11 George Street", result.getBuyer().getStreet());
        assertEquals("South Court", result.getBuyer().getAdditionalStreet());
        assertEquals("London", result.getBuyer().getCity());
        assertEquals("SE1 6HA", result.getBuyer().getPostcode());
        assertEquals("GB", result.getBuyer().getCountryCode());
        assertEquals("GB123456789", result.getBuyer().getVatNumber());

        assertEquals(2, result.getLineItems().size());

        ExtractedInvoice.LineItem line1 = result.getLineItems().get(0);
        assertEquals(1, line1.getLineNumber());
        assertEquals("Consulting services", line1.getDescription());
        assertEquals(new BigDecimal("10.0"), line1.getQuantity());
        assertEquals("HUR", line1.getUnitCode());
        assertEquals(new BigDecimal("100.0"), line1.getUnitPrice());
        assertEquals(0, new BigDecimal("1000.0").compareTo(line1.getLineTotal()));
        assertEquals(new BigDecimal("20"), line1.getVatRate());

        ExtractedInvoice.LineItem line2 = result.getLineItems().get(1);
        assertEquals(2, line2.getLineNumber());
        assertEquals("EA", line2.getUnitCode());
        assertEquals(0, new BigDecimal("250.0").compareTo(line2.getLineTotal()));

        assertNotNull(result.getPaymentDetails());
        assertEquals("Test Bank", result.getPaymentDetails().getBankName());
        assertEquals("123456", result.getPaymentDetails().getSortCode());
        assertEquals("12345678", result.getPaymentDetails().getAccountNumber());
        assertEquals("INV-001", result.getPaymentDetails().getPaymentReference());
    }

    @Test
    void mapToExtractedInvoice_handlesNullContact() {
        FreeAgentInvoice faInvoice = new FreeAgentInvoice();
        faInvoice.setReference("INV-002");
        faInvoice.setDatedOn("2024-03-01");
        faInvoice.setCurrency("GBP");
        faInvoice.setNetValue("500.0");
        faInvoice.setSalesTaxValue("0.0");
        faInvoice.setDueValue("500.0");
        faInvoice.setInvoiceItems(List.of());

        FreeAgentCompany faCompany = new FreeAgentCompany();
        faCompany.setName("Test Co");
        faCompany.setCurrency("GBP");

        ExtractedInvoice result = mapper.mapToExtractedInvoice(faInvoice, null, faCompany);

        assertEquals("INV-002", result.getInvoiceNumber());
        assertNotNull(result.getBuyer());
        assertNull(result.getBuyer().getName());
        assertTrue(result.getLineItems().isEmpty());
    }

    @Test
    void mapToExtractedInvoice_countryMapping() {
        FreeAgentContact contact = new FreeAgentContact();
        contact.setCountry("France");

        FreeAgentInvoice faInvoice = new FreeAgentInvoice();
        faInvoice.setReference("INV-003");
        faInvoice.setCurrency("EUR");
        faInvoice.setInvoiceItems(List.of());

        ExtractedInvoice result = mapper.mapToExtractedInvoice(faInvoice, contact, null);
        assertEquals("FR", result.getBuyer().getCountryCode());
    }

    @Test
    void buildConvertRequest_resolvesFromConfig() {
        ObjectMapper om = new ObjectMapper();
        ObjectNode lookup = om.createObjectNode();
        lookup.put("endpointID", "7300010000001");
        lookup.put("schemeID", "0088");
        when(configService.getBuyerLookup("Acme Ltd")).thenReturn(lookup);

        ExtractedInvoice extracted = new ExtractedInvoice();
        extracted.setCurrency("GBP");
        extracted.setDueDate(LocalDate.of(2024, 2, 14));
        ExtractedInvoice.Party buyer = new ExtractedInvoice.Party();
        buyer.setCompanyName("Acme Ltd");
        extracted.setBuyer(buyer);

        var request = mapper.buildConvertRequest(extracted);

        assertEquals("GBP", request.getCurrency());
        assertEquals("2024-02-14", request.getDueDate());
        assertEquals("7300010000001", request.getBuyerEndpoint());
        assertEquals("0088", request.getBuyerScheme());
    }

    @Test
    void buildConvertRequest_returnsEmptyWhenNoLookup() {
        ExtractedInvoice extracted = new ExtractedInvoice();
        extracted.setCurrency("GBP");
        ExtractedInvoice.Party buyer = new ExtractedInvoice.Party();
        buyer.setCompanyName("Unknown Buyer");
        extracted.setBuyer(buyer);

        var request = mapper.buildConvertRequest(extracted);

        assertEquals("GBP", request.getCurrency());
        assertNull(request.getBuyerEndpoint());
        assertNull(request.getBuyerScheme());
    }
}
