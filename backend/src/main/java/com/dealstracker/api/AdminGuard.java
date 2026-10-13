package com.dealstracker.api;

import com.dealstracker.config.TrackerProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Minimal shared-secret check for admin endpoints until real auth exists. */
@Component
public class AdminGuard {

    public static final String HEADER = "X-Admin-Token";

    private final String token;

    public AdminGuard(TrackerProperties props) {
        this.token = props.adminToken();
    }

    public void check(String provided) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin endpoints are disabled (ADMIN_TOKEN not set)");
        }
        if (provided == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
