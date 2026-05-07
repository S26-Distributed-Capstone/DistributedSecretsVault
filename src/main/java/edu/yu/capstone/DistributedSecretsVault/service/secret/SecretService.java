package edu.yu.capstone.DistributedSecretsVault.service.secret;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.exceptions.DuplicateSecretException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InsufficientShardsException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.util.ClockUtil;

// TODO epoch is only 1L hardcode, needs to be updated when we transition systems to multi-node
@Service
public class SecretService {
    private final SecretPartRepository secretPartRepository; // way to access the data, needs to be updated to
                                                             // incorporate redis or postgres
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
        long epoch = 1L;
        for (SecretPart part : parts) {
            part.setVersion(version);
            part.setEpoch(epoch);
            secretPartRepository.savePart(part); // split to the other nodes
        }
        return buildSecretVersion(key, version, epoch);
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
        long epoch = 1L;
        int totalParts = resolveTotalParts();
        int threshold = resolveThreshold(totalParts);
        List<SecretPart> parts = secretSharingService.split(key, value, threshold, totalParts);
        for (SecretPart part : parts) {
            part.setVersion(version);
            part.setEpoch(epoch);
            if (!secretPartRepository.updatePart(part)) {
                throw new SecretNotFoundException();
            }
        }
        return buildSecretVersion(key, version, epoch);
    }

    public String getSecret(SecretKey key, Long version) {
        validateKey(key);
        if (!secretPartRepository.exists(key)) {
            throw new SecretNotFoundException();
        }
        long resolvedVersion = resolveVersion(key, version);
        List<SecretPart> parts = secretPartRepository.findParts(key, resolvedVersion);
        int threshold = resolveThreshold(resolveTotalParts());
        if (parts.size() < threshold) {
            throw new InsufficientShardsException();
        }
        List<SecretPart> selected = parts.stream()
                .sorted(Comparator.comparingInt(SecretPart::getPartIndex))
                .limit(threshold)
                .toList();
        return secretReconstructionService.reconstruct(selected);
    }

    public Map<Long, String> getAllVersions(SecretKey key) {
        validateKey(key);
        List<Long> versions = secretPartRepository.listVersions(key);
        if (versions.isEmpty()) {
            throw new SecretNotFoundException();
        }
        int threshold = resolveThreshold(resolveTotalParts());
        Map<Long, String> results = new LinkedHashMap<>();
        versions.stream().sorted().forEach(version -> {
            List<SecretPart> parts = secretPartRepository.findParts(key, version);
            if (parts.size() < threshold) {
                throw new InsufficientShardsException();
            }
            List<SecretPart> selected = parts.stream()
                    .sorted(Comparator.comparingInt(SecretPart::getPartIndex))
                    .limit(threshold)
                    .toList();
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

    private SecretVersion buildSecretVersion(SecretKey key, long version, long epoch) {
        SecretVersion secretVersion = new SecretVersion();
        secretVersion.setKey(key);
        secretVersion.setVersion(version);
        secretVersion.setEpoch(epoch);
        secretVersion.setCreatedAtEpochMillis(ClockUtil.nowEpochMillis());
        return secretVersion;
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
