package com.bromleywebworks.peppol.dto.freeagent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class FreeAgentInvoice {
    private String url;
    private String contact;
    private String project;
    @JsonProperty("dated_on")
    private LocalDate datedOn;
    @JsonProperty("due_on")
    private LocalDate dueOn;
    private String reference;
    private String currency;
    @JsonProperty("exchange_rate")
    private BigDecimal exchangeRate;
    @JsonProperty("net_value")
    private BigDecimal netValue;
    @JsonProperty("total_value")
    private BigDecimal totalValue;
    @JsonProperty("paid_value")
    private BigDecimal paidValue;
    @JsonProperty("due_value")
    private BigDecimal dueValue;
    private String status;
    @JsonProperty("ec_status")
    private String ecStatus;
    @JsonProperty("payment_terms_in_days")
    private Integer paymentTermsInDays;
    @JsonProperty("invoice_items")
    private List<FreeAgentInvoiceItem> invoiceItems;
}
