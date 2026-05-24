package edu.yu.capstone.DistributedSecretsVault.service.secret;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.encrypt.SecretSplitter;

/**
 * Converts plaintext secret values into Shamir secret-sharing shards.
 * <p>
 * Internal write services use this adapter to transform a user value into
 * {@link SecretPart} domain objects that can be distributed across cluster nodes.
 */
@Service
public class SecretSharingService {
    /** Stateless wrapper around the Shamir splitting implementation. */
    private final SecretSplitter secretSplitter = new SecretSplitter();

    /**
     * Splits a plaintext secret value into domain shard objects.
     *
     * @param key        composite key identifying the secret being split
     * @param value      plaintext secret value
     * @param threshold  minimum number of shards required to reconstruct the value
     * @param totalParts total number of shards to generate
     * @return list of secret parts with the supplied key and generated shard payloads
     * @throws IllegalArgumentException if the key/name or value is missing
     */
    public List<SecretPart> split(SecretKey key, String value, int threshold, int totalParts) {
        if (key == null || key.getName() == null || key.getName().isBlank()) {
            throw new IllegalArgumentException("Secret key is required");
        }
        if (value == null) {
            throw new IllegalArgumentException("Secret value is required");
        }
        Map<Integer, byte[]> parts = secretSplitter.split(value.getBytes(StandardCharsets.UTF_8), totalParts, threshold);
        List<SecretPart> secretParts = new ArrayList<>(parts.size());
        for (Map.Entry<Integer, byte[]> entry : parts.entrySet()) {
            // Preserve the Shamir share index so reconstruction can identify each point.
            SecretPart part = new SecretPart();
            part.setKey(key);
            part.setPartIndex(entry.getKey());
            part.setShard(entry.getValue());
            secretParts.add(part);
        }
        return secretParts;
    }
}
