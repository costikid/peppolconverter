package com.bromleywebworks.peppol.dto.freeagent;

import lombok.Data;
import java.util.List;

@Data
public class FreeAgentInvoicesResponse {
    private List<FreeAgentInvoiceSummary> invoices;
}
