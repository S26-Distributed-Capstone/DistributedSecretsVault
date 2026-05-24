package edu.yu.capstone.DistributedSecretsVault.encrypt;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import com.codahale.shamir.Scheme;

/**
 * Low-level implementation of Shamir's Secret Sharing scheme.
 * <p>
 * Wraps the {@link com.codahale.shamir.Scheme} library to split a byte-array
 * secret into {@code N} shares such that any {@code K} (threshold) shares can
 * reconstruct the original. When {@code threshold == 1}, all shares are simply
 * copies of the secret (degenerate case).
 *
 * @see SecretSplitter
 * @see SecretReconstructor
 */
public class ShamirSecretSharing {
    /**
     * Splits a secret into multiple shares using Shamir's scheme.
     *
     * @param secret     the raw secret bytes to split
     * @param totalParts total number of shares to generate (N)
     * @param threshold  minimum shares required for reconstruction (K)
     * @return map of share index (1-based) to share bytes
     * @throws IllegalArgumentException if inputs are null, non-positive, or
     *         if {@code threshold > totalParts}
     */
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

    /**
     * Reconstructs a secret from a set of Shamir shares.
     * <p>
     * The number of shares provided determines the threshold used for
     * reconstruction. When only one share is present, it is returned directly
     * (degenerate case).
     *
     * @param parts map of share index (1-based) to share bytes
     * @return the reconstructed secret bytes
     * @throws IllegalArgumentException if {@code parts} is null or empty
     */
    public byte[] reconstruct(Map<Integer, byte[]> parts) {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("Secret parts are required");
        }
        if (parts.size() == 1) {
            return parts.values().iterator().next();
        }
        int totalParts = parts.keySet().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElseThrow(() -> new IllegalArgumentException("Secret parts are required"));
        Scheme scheme = new Scheme(new SecureRandom(), totalParts, parts.size());
        return scheme.join(parts);
    }
}
