package edu.yu.capstone.DistributedSecretsVault.util;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;

public final class SecretKeyGenerator {
    private SecretKeyGenerator() {
    }

    public static SecretKey of(String ownerId, String name) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Secret key is required");
        }
        SecretKey key = new SecretKey();
        key.setOwnerId(ownerId);
        key.setName(name);
        return key;
    }
}
