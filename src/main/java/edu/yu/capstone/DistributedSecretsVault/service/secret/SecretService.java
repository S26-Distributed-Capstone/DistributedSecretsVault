package edu.yu.capstone.DistributedSecretsVault.service.secret;

import java.util.Map;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;

public interface SecretService {
    SecretVersion storeSecret(SecretKey key, String value);

    SecretVersion updateSecret(SecretKey key, String value);

    String getSecret(SecretKey key, Long version);

    Map<Long, String> getAllVersions(SecretKey key);

    void deleteSecret(SecretKey key);
}
