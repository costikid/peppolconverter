package com.bromleywebworks.peppol.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import javax.validation.constraints.NotNull;

@Data
public class UploadForm {

    @NotNull(message = "Please select a PDF file to upload")
    private MultipartFile pdfFile;

    private String buyerEndpoint;
    private String buyerScheme;
    private String vatCategory;
    private String dueDate;
    private String currency;
    private String buyerStreet;
    private String buyerCity;
    private String buyerPostcode;
    private String buyerCountryCode;
}
