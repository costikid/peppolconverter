package com.bromleywebworks.peppol.dto.freeagent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FreeAgentCompany {

    @JsonProperty("name")
    private String name;

    @JsonProperty("company_registration_number")
    private String companyRegistrationNumber;

    @JsonProperty("sales_tax_registration_status")
    private String salesTaxRegistrationStatus;

    @JsonProperty("sales_tax_registration_number")
    private String salesTaxRegistrationNumber;

    @JsonProperty("currency")
    private String currency;
}
