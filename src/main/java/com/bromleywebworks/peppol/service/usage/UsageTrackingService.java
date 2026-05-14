package com.bromleywebworks.peppol.service.usage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageTrackingService {

    private static final int FREE_LIMIT = 2;
    private final UsageRepository usageRepository;

    public int incrementUsage(String userId) {
        int count = usageRepository.incrementUsage(userId);
        log.info("Usage incremented for user {}: {}/{} today", userId, count, FREE_LIMIT);
        return count;
    }

    public int getUsage(String userId) {
        return usageRepository.getUsage(userId);
    }

    public boolean isLimitExceeded(int count) {
        return count > FREE_LIMIT;
    }

    public int getRemaining(int count) {
        return Math.max(0, FREE_LIMIT - count);
    }
}
