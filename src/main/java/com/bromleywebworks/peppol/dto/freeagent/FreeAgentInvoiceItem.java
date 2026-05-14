package com.bromleywebworks.peppol.dto.freeagent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class FreeAgentInvoiceItem {
    private String description;
    @JsonProperty("item_type")
    private String itemType;
    private BigDecimal price;
    private BigDecimal quantity;
    @JsonProperty("sales_tax_rate")
    private BigDecimal salesTaxRate;
}
