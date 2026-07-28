package com.bromleywebworks.peppol.dto.freeagent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FreeAgentInvoiceSummary {

    @JsonProperty("url")
    private String url;

    @JsonProperty("contact")
    private String contact;

    @JsonProperty("contact_name")
    private String contactName;

    @JsonProperty("dated_on")
    private String datedOn;

    @JsonProperty("due_on")
    private String dueOn;

    @JsonProperty("reference")
    private String reference;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("net_value")
    private String netValue;

    @JsonProperty("sales_tax_value")
    private String salesTaxValue;

    @JsonProperty("total_value")
    private String totalValue;

    @JsonProperty("paid_value")
    private String paidValue;

    @JsonProperty("due_value")
    private String dueValue;

    @JsonProperty("status")
    private String status;

    @JsonProperty("ec_status")
    private String ecStatus;
}
