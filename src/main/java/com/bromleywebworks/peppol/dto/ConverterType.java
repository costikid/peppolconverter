package com.bromleywebworks.peppol.dto;

import java.util.Arrays;

public enum ConverterType {
    FREEAGENT("freeagent", "FreeAgent"),
    XERO("xero", "Xero"),
    QUICKBOOKS("quickbooks", "QuickBooks");

    private final String slug;
    private final String displayName;

    ConverterType(String slug, String displayName) {
        this.slug = slug;
        this.displayName = displayName;
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ConverterType fromSlug(String slug) {
        return Arrays.stream(values())
                .filter(type -> type.slug.equals(slug))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown converter slug: " + slug));
    }
}
