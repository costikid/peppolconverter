package com.bromleywebworks.peppol.dto.freeagent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class FreeAgentCompany {
    private String name;
    @JsonProperty("company_registration_number")
    private String companyRegistrationNumber;
    @JsonProperty("sales_tax_registration_number")
    private String salesTaxRegistrationNumber;
    @JsonProperty("sales_tax_registration_status")
    private String salesTaxRegistrationStatus;
}
