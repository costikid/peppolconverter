package com.bromleywebworks.peppol.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final int MAX_REQUESTS_PER_MINUTE = 10;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(2))
            .build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/api/convert") && !path.equals("/freeagent-to-peppol/upload");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String clientIp = getClientIp(request);
        Bucket bucket = buckets.get(clientIp, this::createBucket);

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":\"rate_limit_exceeded\","
                    + "\"message\":\"Too many requests. Please wait before uploading again.\"}");
        }
    }

    private Bucket createBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(
                MAX_REQUESTS_PER_MINUTE,
                Refill.greedy(MAX_REQUESTS_PER_MINUTE, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String ip) {
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)
                || ip.startsWith("10.") || ip.startsWith("192.168.")
                || (ip.startsWith("172.") && ip.compareTo("172.16.") >= 0 && ip.compareTo("172.31.255.255") <= 0);
    }
}
