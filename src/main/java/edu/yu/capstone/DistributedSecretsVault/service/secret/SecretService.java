package edu.yu.capstone.DistributedSecretsVault.service.secret;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.exceptions.DuplicateSecretException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InsufficientShardsException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

@Service
public class SecretService {
    private final SecretPartRepository secretPartRepository;
    private final SecretSharingService secretSharingService;
    private final SecretReconstructionService secretReconstructionService;
    private final ClusterConfig clusterConfig;

    public SecretService(SecretPartRepository secretPartRepository,
            SecretSharingService secretSharingService,
            SecretReconstructionService secretReconstructionService,
            ClusterConfig clusterConfig) {
        this.secretPartRepository = secretPartRepository;
        this.secretSharingService = secretSharingService;
        this.secretReconstructionService = secretReconstructionService;
        this.clusterConfig = clusterConfig;
    }

    public SecretVersion storeSecret(SecretKey key, String value) {
        validateKey(key);
        if (value == null) {
            throw new IllegalArgumentException("Secret value is required");
        }
        if (secretPartRepository.exists(key)) {
            throw new DuplicateSecretException();
        }
        int totalParts = resolveTotalParts();
        int threshold = resolveThreshold(totalParts);
        List<SecretPart> parts = secretSharingService.split(key, value, threshold, totalParts);
        long version = 1L;
        for (SecretPart part : parts) {
            part.setVersion(version);
            secretPartRepository.savePart(part); // split to the other nodes
        }
        return new SecretVersion(key, version, System.currentTimeMillis());
    }

    public SecretVersion updateSecret(SecretKey key, String value) {
        validateKey(key);
        if (value == null) {
            throw new IllegalArgumentException("Secret value is required");
        }
        if (!secretPartRepository.exists(key)) {
            throw new SecretNotFoundException();
        }
        long version = nextVersion(key);
        int totalParts = resolveTotalParts();
        int threshold = resolveThreshold(totalParts);
        List<SecretPart> parts = secretSharingService.split(key, value, threshold, totalParts);
        for (SecretPart part : parts) {
            part.setVersion(version);
            if (!secretPartRepository.updatePart(part)) {
                throw new SecretNotFoundException();
            }
        }
        return new SecretVersion(key, version, System.currentTimeMillis());
    }

    public String getSecret(SecretKey key, Long version) {
        validateKey(key);
        if (!secretPartRepository.exists(key)) {
            throw new SecretNotFoundException();
        }
        long resolvedVersion = resolveVersion(key, version);
        Optional<SecretPart> part = secretPartRepository.findPart(key, resolvedVersion);
        if (part.isEmpty()) {
            throw new InsufficientShardsException();
        }
        List<SecretPart> selected = part.stream().toList();
        return secretReconstructionService.reconstruct(selected);
    }

    public Map<Long, String> getAllVersions(SecretKey key) {
        validateKey(key);
        List<Long> versions = secretPartRepository.listVersions(key);
        if (versions.isEmpty()) {
            throw new SecretNotFoundException();
        }
        Map<Long, String> results = new LinkedHashMap<>();
        versions.stream().sorted().forEach(version -> {
            Optional<SecretPart> part = secretPartRepository.findPart(key, version);
            if (part.isEmpty()) {
                throw new InsufficientShardsException();
            }
            List<SecretPart> selected = part.stream().toList();
            results.put(version, secretReconstructionService.reconstruct(selected));
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

    private long resolveVersion(SecretKey key, Long requestedVersion) {
        if (requestedVersion != null) {
            List<Long> versions = secretPartRepository.listVersions(key);
            if (!versions.contains(requestedVersion)) {
                throw new SecretNotFoundException();
            }
            return requestedVersion;
        }
        return latestVersion(key);
    }

    private long latestVersion(SecretKey key) {
        return secretPartRepository.findLatest(key)
                .map(SecretPart::getVersion)
                .orElseThrow(SecretNotFoundException::new);
    }

    private long nextVersion(SecretKey key) {
        return secretPartRepository.findLatest(key)
                .map(part -> part.getVersion() + 1L)
                .orElse(1L);
    }

    private int resolveTotalParts() {
        if (clusterConfig == null || clusterConfig.getTotalNodes() <= 0) {
            return 1;
        }
        return clusterConfig.getTotalNodes();
    }

    private int resolveThreshold(int totalParts) {
        int threshold = clusterConfig == null ? 0 : clusterConfig.getThresholdK();
        if (threshold <= 0) {
            threshold = 1;
        }
        if (threshold > totalParts) {
            threshold = totalParts;
        }
        return threshold;
    }
}
