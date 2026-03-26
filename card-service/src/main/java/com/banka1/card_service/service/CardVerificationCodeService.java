package com.banka1.card_service.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Generates and hashes one-time numeric verification codes for card requests.
 */
@Service
public class CardVerificationCodeService {

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a six-digit verification code.
     *
     * @return six-digit numeric code
     */
    public String generateCode() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }

    /**
     * Hashes the provided verification code using SHA-256.
     *
     * @param value raw verification code
     * @return lowercase hex digest
     */
    public String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }
}
