package com.bromleywebworks.peppol.dto;

import lombok.Data;

@Data
public class AddressForm {
    private String street;
    private String additionalStreet;
    private String city;
    private String postcode;
    private String countryCode;
}
