package edu.yu.capstone.DistributedSecretsVault.encrypt;

import java.util.Map;

/**
 * Convenience wrapper that delegates secret splitting to {@link ShamirSecretSharing}.
 * <p>
 * Used by {@link edu.yu.capstone.DistributedSecretsVault.service.secret.SecretSharingService}
 * to convert a plaintext secret value into Shamir shares.
 */
public class SecretSplitter {
    /**
     * Splits a secret into Shamir shares.
     *
     * @param secret     raw secret bytes
     * @param totalParts total number of shares to generate
     * @param threshold  minimum shares required to reconstruct
     * @return map of share index (1-based) to share bytes
     */
    public Map<Integer, byte[]> split(byte[] secret, int totalParts, int threshold) {
        return new ShamirSecretSharing().split(secret, totalParts, threshold);
    }
}
