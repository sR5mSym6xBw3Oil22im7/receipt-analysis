package com.example.receipt.config;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class AdminAccessTokenService {
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(8);
    private static final String SIGNING_ALGORITHM = "HmacSHA256";
    private final SecretKeySpec signingKey;

    public AdminAccessTokenService(@Value("${ADMIN_PASSWORD_HASH:local-test-only-token-key}") String adminPasswordHash) {
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(
                    ("receipt-analysis-admin-token:" + adminPasswordHash).getBytes(StandardCharsets.UTF_8));
            this.signingKey = new SecretKeySpec(key, SIGNING_ALGORITHM);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not initialize the admin token signing key", exception);
        }
    }

    public String issue(String username) {
        String payload = username + "\n" + Instant.now().plus(TOKEN_LIFETIME).getEpochSecond();
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    public Authentication authenticate(String token) {
        if (token == null) return null;
        int separator = token.indexOf('.');
        if (separator <= 0 || separator != token.lastIndexOf('.')) return null;

        String encodedPayload = token.substring(0, separator);
        byte[] suppliedSignature;
        try {
            suppliedSignature = Base64.getUrlDecoder().decode(token.substring(separator + 1));
        } catch (IllegalArgumentException exception) {
            return null;
        }
        byte[] expectedSignature;
        try {
            expectedSignature = Base64.getUrlDecoder().decode(sign(encodedPayload));
        } catch (IllegalArgumentException exception) {
            return null;
        }
        if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) return null;

        try {
            String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            int payloadSeparator = payload.lastIndexOf('\n');
            if (payloadSeparator <= 0) return null;
            String username = payload.substring(0, payloadSeparator);
            long expiresAt = Long.parseLong(payload.substring(payloadSeparator + 1));
            if (username.isBlank() || expiresAt <= Instant.now().getEpochSecond()) return null;
            return UsernamePasswordAuthenticationToken.authenticated(
                    username, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(SIGNING_ALGORITHM);
            mac.init(signingKey);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not sign the admin access token", exception);
        }
    }
}
