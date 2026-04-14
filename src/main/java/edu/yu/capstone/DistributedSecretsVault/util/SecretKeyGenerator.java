package edu.yu.capstone.DistributedSecretsVault.util;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;

public final class SecretKeyGenerator {
    private SecretKeyGenerator() {
    }

    public static SecretKey of(String ownerId, String name) {
        SecretKey key = new SecretKey();
        key.setOwnerId(ownerId);
        key.setName(name);
        return key;
    }
}
