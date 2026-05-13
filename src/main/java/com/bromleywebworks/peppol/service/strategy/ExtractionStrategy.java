package com.bromleywebworks.peppol.service.strategy;

import com.bromleywebworks.peppol.dto.ExtractedInvoice;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ExtractionStrategy {

    ExtractedInvoice extract(MultipartFile file) throws IOException;

    String getSupportedType();
}
