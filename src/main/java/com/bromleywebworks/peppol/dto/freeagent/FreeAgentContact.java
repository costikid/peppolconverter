package com.bromleywebworks.peppol.dto.freeagent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class FreeAgentContact {
    @JsonProperty("organisation_name")
    private String organisationName;
    @JsonProperty("first_name")
    private String firstName;
    @JsonProperty("last_name")
    private String lastName;
    private String address1;
    private String address2;
    private String address3;
    private String town;
    private String region;
    private String postcode;
    private String country;
    @JsonProperty("sales_tax_registration_number")
    private String salesTaxRegistrationNumber;
}
