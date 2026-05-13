package com.bromleywebworks.peppol.dto;

import lombok.Data;

@Data
public class ConvertRequest {

    private String buyerEndpoint;
    private String buyerScheme;
    private String dueDate;
    private String currency;
    private String vatCategory;
}
