package com.bromleywebworks.peppol.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class BlogPost {
    private String slug;
    private String title;
    private LocalDate date;
    private String summary;
    private String htmlContent;
}
