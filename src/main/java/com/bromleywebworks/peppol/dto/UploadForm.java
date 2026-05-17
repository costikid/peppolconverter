package com.bromleywebworks.peppol.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import javax.validation.constraints.NotNull;

@Data
public class UploadForm {

    @NotNull(message = "Please select a PDF file to upload")
    private MultipartFile pdfFile;

    @NotNull(message = "Buyer Endpoint is required")
    private String buyerEndpoint;

    @NotNull(message = "Buyer Scheme is required")
    private String buyerScheme;

    private String vatCategory;
    private String dueDate;
    private String currency;
}
