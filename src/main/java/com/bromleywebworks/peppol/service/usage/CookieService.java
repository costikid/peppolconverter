package com.bromleywebworks.peppol.service.usage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.cookie.secret")
public class CookieService {

    private static final String COOKIE_NAME = "user_id";
    private static final int COOKIE_MAX_AGE = 30 * 24 * 60 * 60; // 30 days
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String cookieSecret;
    private final String cookieSecretPrevious;
    private final boolean isProduction;

    public CookieService(
            @Value("${app.cookie.secret}") String cookieSecret,
            @Value("${app.cookie.secret-previous:}") String cookieSecretPrevious,
            @Value("${spring.profiles.active:local}") String activeProfile) {
        this.cookieSecret = cookieSecret;
        this.cookieSecretPrevious = cookieSecretPrevious;
        this.isProduction = "prod".equals(activeProfile);
    }

    public String getOrCreateUserId(HttpServletRequest request, HttpServletResponse response) {
        String userId = extractUserIdFromCookie(request);
        if (userId != null) {
            return userId;
        }

        // Create new signed cookie
        String newUserId = UUID.randomUUID().toString();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = sign(newUserId + "|" + timestamp, cookieSecret);
        String cookieValue = newUserId + "|" + timestamp + "|" + signature;

        Cookie cookie = new Cookie(COOKIE_NAME, cookieValue);
        cookie.setMaxAge(COOKIE_MAX_AGE);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(isProduction);
        // Spring doesn't directly support SameSite in Cookie object, handled by Spring Security or custom filter
        // For now, rely on HttpOnly + Secure + path
        response.addCookie(cookie);

        return newUserId;
    }

    private String extractUserIdFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;

        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return validateAndExtractUserId(cookie.getValue());
            }
        }
        return null;
    }

    private String validateAndExtractUserId(String cookieValue) {
        if (cookieValue == null || !cookieValue.contains("|")) return null;

        String[] parts = cookieValue.split("\\|");
        if (parts.length != 3) return null;

        String userId = parts[0];
        String timestamp = parts[1];
        String signature = parts[2];

        String payload = userId + "|" + timestamp;

        // Try current secret first
        if (signature.equals(sign(payload, cookieSecret))) {
            return userId;
        }

        // Fall back to previous secret (key rotation support)
        if (!cookieSecretPrevious.isBlank() && signature.equals(sign(payload, cookieSecretPrevious))) {
            return userId;
        }

        log.warn("Invalid cookie signature detected");
        return null;
    }

    private String sign(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign cookie", e);
        }
    }
}
