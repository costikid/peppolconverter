package com.bromleywebworks.peppol.service.usage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

@Slf4j
@Repository
@ConditionalOnProperty(name = "redis.enabled", havingValue = "true")
public class RedisUsageRepository implements UsageRepository {

    private static final String KEY_PREFIX = "usage:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE;
    private static final int TTL_SECONDS = 24 * 60 * 60; // 24 hours

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> incrementScript;

    // Lua script for atomic INCR + EXPIRE (prevents zombie keys if app crashes)
    private static final String LUA_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) " +
            "if count == 1 then " +
            "  redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "return count";

    public RedisUsageRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.incrementScript = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
    }

    @Override
    public int incrementUsage(String userId) {
        String key = buildKey(userId);
        Long count = redisTemplate.execute(
                incrementScript,
                Collections.singletonList(key),
                String.valueOf(TTL_SECONDS)
        );
        return count != null ? count.intValue() : 0;
    }

    @Override
    public int getUsage(String userId) {
        String key = buildKey(userId);
        String value = redisTemplate.opsForValue().get(key);
        try {
            return value != null ? Integer.parseInt(value) : 0;
        } catch (NumberFormatException e) {
            log.warn("Invalid usage value in Redis for key {}: {}", key, value);
            return 0;
        }
    }

    private String buildKey(String userId) {
        String date = LocalDate.now().format(DATE_FORMAT);
        return KEY_PREFIX + userId + ":" + date;
    }
}
