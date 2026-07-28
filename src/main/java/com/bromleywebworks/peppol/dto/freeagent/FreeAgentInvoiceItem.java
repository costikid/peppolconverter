package com.bromleywebworks.peppol.dto.freeagent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FreeAgentInvoiceItem {

    @JsonProperty("description")
    private String description;

    @JsonProperty("item_type")
    private String itemType;

    @JsonProperty("price")
    private String price;

    @JsonProperty("quantity")
    private String quantity;

    @JsonProperty("sales_tax_rate")
    private String salesTaxRate;

    @JsonProperty("category")
    private String category;

    @JsonProperty("position")
    private Integer position;
}
