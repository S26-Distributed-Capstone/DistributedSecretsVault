package edu.yu.capstone.DistributedSecretsVault.encrypt;

import java.util.Map;

public class SecretReconstructor {
    public byte[] reconstruct(Map<Integer, byte[]> parts) {
        return new ShamirSecretSharing().reconstruct(parts);
    }
}
