package com.bromleywebworks.peppol.service.strategy;

import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreeAgentExtractionStrategyTest {

    @Test
    void parsesMedianodeInvoiceLayout() throws Exception {
        String sample = """
                MEDIANODE
                MediaNode
                50 Textile Street
                London
                EC2A 6QT
                VAT: 12345678

                John Doe
                Client Company
                Client Street Address
                Street Address 2
                Street Address 3
                Edinburgh
                Region EH3 9JB
                VAT: 000000000

                INVOICE 025063
                11 December 2012
                Payment: 20 Days
                Payment due by 31 December 2012

                Quantity  Details  Unit Price (£)  VAT  Net Subtotal (£)
                1 Day Details of project activity to be billed 500.00 20% 500.00
                2 Days Other details of project activity to be billed 1,000.00 20% 2,000.00
                3 Days More details of project activity to be billed 1,500.00 20% 4,500.00

                Net Total £7,000.00
                VAT @ 20% £1,400.00
                GBP Total £8,400.00
                """;

        FreeAgentExtractionStrategy strategy = new FreeAgentExtractionStrategy();
        Method parser = FreeAgentExtractionStrategy.class.getDeclaredMethod("parseFreeAgentText", String.class);
        parser.setAccessible(true);
        ExtractedInvoice invoice = (ExtractedInvoice) parser.invoke(strategy, sample);

        assertEquals("025063", invoice.getInvoiceNumber());
        assertNotNull(invoice.getIssueDate());
        assertNotNull(invoice.getSeller());
        assertNotNull(invoice.getBuyer());

        assertEquals(3, invoice.getLineItems().size());
        assertEquals(new BigDecimal("20"), invoice.getLineItems().get(0).getVatRate());
        assertEquals(new BigDecimal("7000.00"), invoice.getTotalAmount());
        assertEquals(new BigDecimal("1400.00"), invoice.getVatAmount());
        assertEquals(new BigDecimal("8400.00"), invoice.getDueAmount());
        assertTrue(invoice.getLineItems().stream().allMatch(item -> item.getVatRate().compareTo(BigDecimal.ZERO) > 0));
    }
}
