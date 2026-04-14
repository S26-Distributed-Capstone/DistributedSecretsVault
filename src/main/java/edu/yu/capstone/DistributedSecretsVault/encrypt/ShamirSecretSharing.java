package edu.yu.capstone.DistributedSecretsVault.encrypt;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import com.codahale.shamir.Scheme;

public class ShamirSecretSharing {
    public Map<Integer, byte[]> split(byte[] secret, int totalParts, int threshold) {
        if (secret == null) {
            throw new IllegalArgumentException("Secret bytes are required");
        }
        if (totalParts <= 0 || threshold <= 0) {
            throw new IllegalArgumentException("Total parts and threshold must be positive");
        }
        if (threshold > totalParts) {
            throw new IllegalArgumentException("Threshold cannot exceed total parts");
        }
        if (threshold == 1) {
            Map<Integer, byte[]> parts = new HashMap<>();
            for (int i = 1; i <= totalParts; i++) {
                parts.put(i, secret);
            }
            return parts;
        }
        Scheme scheme = new Scheme(new SecureRandom(), totalParts, threshold);
        return scheme.split(secret);
    }

    public byte[] reconstruct(Map<Integer, byte[]> parts) {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("Secret parts are required");
        }
        if (parts.size() == 1) {
            return parts.values().iterator().next();
        }
        int size = parts.size();
        Scheme scheme = new Scheme(new SecureRandom(), size, size);
        return scheme.join(parts);
    }
}
