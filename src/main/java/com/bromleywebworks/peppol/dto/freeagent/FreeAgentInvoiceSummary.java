package com.bromleywebworks.peppol.dto.freeagent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FreeAgentInvoiceSummary {
    private String url;
    private String reference;
    @JsonProperty("dated_on")
    private LocalDate datedOn;
    @JsonProperty("due_on")
    private LocalDate dueOn;
    private String currency;
    @JsonProperty("total_value")
    private BigDecimal totalValue;
    private String status;
    @JsonProperty("long_status")
    private String longStatus;
}
