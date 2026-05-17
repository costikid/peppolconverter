package com.bromleywebworks.peppol.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ExtractedInvoice {

    private String uuid;
    private String invoiceNumber;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private String currency;
    private BigDecimal totalAmount;
    private BigDecimal vatAmount;
    private BigDecimal paidAmount;
    private BigDecimal dueAmount;
    private String ecStatus;
    private String paymentMethod;

    private Party seller;
    private Party buyer;
    private List<LineItem> lineItems;
    private PaymentDetails paymentDetails;

    @Data
    public static class Party {
        private String name;
        private String companyName;
        private String street;
        private String additionalStreet;
        private String city;
        private String postcode;
        private String countryCode;
        private String vatNumber;

    }

    @Data
    public static class LineItem {
        private int lineNumber;
        private BigDecimal quantity;
        private String unitCode;
        private String description;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private BigDecimal vatRate;

    }

    @Data
    public static class PaymentDetails {
        private String bankName;
        private String sortCode;
        private String accountNumber;
        private String paymentReference;
        private LocalDate paymentDate;
        private BigDecimal paymentAmount;

    }
}
