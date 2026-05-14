package com.bromleywebworks.peppol.service.usage;

public interface UsageRepository {
    int incrementUsage(String userId);
    int getUsage(String userId);
}
