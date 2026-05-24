package edu.yu.capstone.DistributedSecretsVault.encrypt;

import java.util.Map;

/**
 * Convenience wrapper that delegates secret reconstruction to {@link ShamirSecretSharing}.
 * <p>
 * Used by {@link edu.yu.capstone.DistributedSecretsVault.service.secret.SecretReconstructionService}
 * to reassemble a plaintext secret from collected Shamir shares.
 */
public class SecretReconstructor {
    /**
     * Reconstructs a secret from Shamir shares.
     *
     * @param parts map of share index (1-based) to share bytes
     * @return the reconstructed secret bytes
     */
    public byte[] reconstruct(Map<Integer, byte[]> parts) {
        return new ShamirSecretSharing().reconstruct(parts);
    }
}
