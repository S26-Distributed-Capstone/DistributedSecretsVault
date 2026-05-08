package edu.yu.capstone.DistributedSecretsVault.service.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

@Service
public class ShardService {
    private final SecretPartRepository secretPartRepository;

    public ShardService(SecretPartRepository secretPartRepository) {
        this.secretPartRepository = secretPartRepository;
    }

    public SecretVersion storeSecret(SecretKey key, String value) {
        return null;
    }

    public SecretVersion updateSecret(SecretKey key, String value) {
        return null;
    }

    public SecretPart getShard(SecretKey key, Long version) {
        validateKey(key);
        if (!secretPartRepository.exists(key)) {
            throw new SecretNotFoundException();
        }
        Optional<SecretPart> part = Optional.empty();
        if (version == null) {
            part = secretPartRepository.findLatest(key);
        } else {
            part = secretPartRepository.findPart(key, version);
        }
        if (part.isEmpty()) {
            throw new SecretNotFoundException();
        }
        return part.get();
    }

    public Map<Long, SecretPart> getAllVersions(SecretKey key) {
        validateKey(key);
        List<Long> versions = secretPartRepository.listVersions(key);
        if (versions.isEmpty()) {
            throw new SecretNotFoundException();
        }
        Map<Long, SecretPart> results = new LinkedHashMap<>();
        versions.stream().sorted().forEach(version -> {
            Optional<SecretPart> part = secretPartRepository.findPart(key, version);
            if (part.isEmpty()) {
                throw new SecretNotFoundException();
            }
            results.put(version, part.get());
        });
        return results;
    }

    public void deleteSecret(SecretKey key) {
        validateKey(key);
        if (!secretPartRepository.exists(key)) {
            throw new SecretNotFoundException();
        }
        secretPartRepository.deleteParts(key);
    }

    private void validateKey(SecretKey key) {
        if (key == null || key.getName() == null || key.getName().isBlank()) {
            throw new IllegalArgumentException("Secret key is required");
        }
    }
}
