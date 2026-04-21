package edu.yu.capstone.DistributedSecretsVault.repository.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

@Repository
@Profile("!single-redis")
public class InMemorySecretPartRepository implements SecretPartRepository {
    private final Map<String, SecretPart> parts = new ConcurrentHashMap<>();

    @Override
    public Optional<SecretPart> findPart(SecretKey key, long version, int partIndex) {
        return Optional.ofNullable(parts.get(composeKey(key, version, partIndex)));
    }

    @Override
    public List<SecretPart> findParts(SecretKey key, long version) {
        return parts.values().stream()
                .filter(part -> matchesKey(part, key) && part.getVersion() == version)
                .toList();
    }

    @Override
    public Optional<SecretPart> findLatest(SecretKey key) {
        return parts.values().stream()
                .filter(part -> matchesKey(part, key))
                .max(Comparator.comparingLong(SecretPart::getVersion)
                        .thenComparingInt(SecretPart::getPartIndex));
    }

    @Override
    public List<Long> listVersions(SecretKey key) {
        return parts.values().stream()
                .filter(part -> matchesKey(part, key))
                .map(SecretPart::getVersion)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public boolean exists(SecretKey key) {
        return parts.values().stream().anyMatch(part -> matchesKey(part, key));
    }

    @Override
    public void savePart(SecretPart part) {
        if (part == null || part.getKey() == null) {
            throw new IllegalArgumentException("SecretPart and key are required");
        }
        parts.put(composeKey(part.getKey(), part.getVersion(), part.getPartIndex()), part);
    }

    @Override
    public boolean updatePart(SecretPart part) {
        if (part == null || part.getKey() == null) {
            throw new IllegalArgumentException("SecretPart and key are required");
        }
        if (!exists(part.getKey())) {
            return false;
        }
        savePart(part);
        return true;
    }

    @Override
    public void deleteParts(SecretKey key) {
        parts.entrySet().removeIf(entry -> matchesKey(entry.getValue(), key));
    }

    private boolean matchesKey(SecretPart part, SecretKey key) {
        if (part == null || key == null || part.getKey() == null) {
            return false;
        }
        return Objects.equals(part.getKey().getOwnerId(), key.getOwnerId())
                && Objects.equals(part.getKey().getName(), key.getName());
    }

    private String composeKey(SecretKey key, long version, int partIndex) {
        return String.valueOf(key.getOwnerId()) + "|" + String.valueOf(key.getName()) + "|" + version + "|" + partIndex;
    }
}
