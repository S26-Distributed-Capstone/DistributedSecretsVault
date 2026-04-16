package edu.yu.capstone.DistributedSecretsVault.encrypt;

import java.util.Map;

public class SecretSplitter {
    public Map<Integer, byte[]> split(byte[] secret, int totalParts, int threshold) {
        return new ShamirSecretSharing().split(secret, totalParts, threshold);
    }
}
