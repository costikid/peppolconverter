package com.bromleywebworks.peppol.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class LineItemForm {
    private Integer lineNumber;
    private String description;
    private BigDecimal quantity;
    private String unitCode;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    private BigDecimal vatRate;
}
