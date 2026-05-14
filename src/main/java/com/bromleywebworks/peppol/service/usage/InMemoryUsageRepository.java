package com.bromleywebworks.peppol.service.usage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
@ConditionalOnProperty(name = "redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryUsageRepository implements UsageRepository {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE;
    private final ConcurrentHashMap<String, Integer> usageMap = new ConcurrentHashMap<>();

    @Override
    public int incrementUsage(String userId) {
        String key = buildKey(userId);
        return usageMap.merge(key, 1, Integer::sum);
    }

    @Override
    public int getUsage(String userId) {
        String key = buildKey(userId);
        return usageMap.getOrDefault(key, 0);
    }

    private String buildKey(String userId) {
        String date = LocalDate.now().format(DATE_FORMAT);
        return userId + ":" + date;
    }
}
