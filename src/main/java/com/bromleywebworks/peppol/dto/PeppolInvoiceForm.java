package com.bromleywebworks.peppol.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class PeppolInvoiceForm {
    // Invoice metadata
    private String invoiceId;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate issueDate;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;
    private String currency;
    private String invoiceTypeCode = "380";

    // Seller party
    private String sellerName;
    private String sellerEndpointId;
    private String sellerSchemeId;
    private String sellerVatNumber;
    private String sellerCompanyNumber;
    private AddressForm sellerAddress;
    private ContactForm sellerContact;

    // Buyer party
    private String buyerName;
    private String buyerEndpointId;
    private String buyerSchemeId;
    private AddressForm buyerAddress;

    // Line items
    private List<LineItemForm> lineItems = new ArrayList<>();

    // Payment details
    private String bankName;
    private String sortCode;
    private String accountNumber;
    private String paymentReference;

    // Totals
    private BigDecimal subtotal;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount;
    private String vatCategory;
}
