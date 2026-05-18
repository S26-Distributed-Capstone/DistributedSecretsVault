package edu.yu.capstone.DistributedSecretsVault.service.secret;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.exceptions.DuplicateSecretException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InsufficientShardsException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.SecretPartResponse;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.SecretPartsResponse;

@Service
public class SecretService {
    private final SecretPartRepository secretPartRepository;
    private final SecretSharingService secretSharingService;
    private final SecretReconstructionService secretReconstructionService;
    private final NodeClient nodeClient;
    private final ClusterConfig clusterConfig;

    public SecretService(SecretPartRepository secretPartRepository,
            SecretSharingService secretSharingService,
            SecretReconstructionService secretReconstructionService,
            NodeClient nodeClient,
            ClusterConfig clusterConfig) {
        this.secretPartRepository = secretPartRepository;
        this.secretSharingService = secretSharingService;
        this.secretReconstructionService = secretReconstructionService;
        this.nodeClient = nodeClient;
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
        List<SecretPart> selected = collectPartsForReconstruction(key, version);
        return secretReconstructionService.reconstruct(selected);
    }

    public Map<Long, String> getAllVersions(SecretKey key) {
        validateKey(key);
        Map<Long, Map<Integer, SecretPart>> partsByVersion = collectAllPartsByVersion(key);
        if (partsByVersion.isEmpty()) {
            throw new SecretNotFoundException();
        }
        Map<Long, String> results = new LinkedHashMap<>();
        partsByVersion.keySet().stream().sorted().forEach(version -> {
            List<SecretPart> selected = requireThreshold(partsByVersion.get(version));
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

    private long nextVersion(SecretKey key) {
        return secretPartRepository.findLatest(key)
                .map(part -> part.getVersion() + 1L)
                .orElse(1L);
    }

    private List<SecretPart> collectPartsForReconstruction(SecretKey key, Long requestedVersion) {
        Map<Long, Map<Integer, SecretPart>> partsByVersion = new LinkedHashMap<>();
        addLocalPart(partsByVersion, key, requestedVersion);

        for (String peerUrl : resolvePeerUrls()) {
            SecretPartResponse response = nodeClient.fetchSecretPart(peerUrl, key, requestedVersion);
            if (response != null && response.found()) {
                addPart(partsByVersion, response.part());
            }
        }

        if (partsByVersion.isEmpty()) {
            throw new SecretNotFoundException();
        }

        long resolvedVersion = requestedVersion == null
                ? partsByVersion.keySet().stream().mapToLong(Long::longValue).max()
                        .orElseThrow(SecretNotFoundException::new)
                : requestedVersion;

        Map<Integer, SecretPart> selectedParts = partsByVersion.get(resolvedVersion);
        if (selectedParts == null || selectedParts.isEmpty()) {
            throw new SecretNotFoundException();
        }
        return requireThreshold(selectedParts);
    }

    private Map<Long, Map<Integer, SecretPart>> collectAllPartsByVersion(SecretKey key) {
        Map<Long, Map<Integer, SecretPart>> partsByVersion = new LinkedHashMap<>();
        addLocalVersionParts(partsByVersion, key);

        for (String peerUrl : resolvePeerUrls()) {
            SecretPartsResponse response = nodeClient.fetchAllSecretParts(peerUrl, key);
            if (response != null && response.found()) {
                response.parts().values().forEach(part -> addPart(partsByVersion, part));
            }
        }
        return partsByVersion;
    }

    private void addLocalPart(Map<Long, Map<Integer, SecretPart>> partsByVersion, SecretKey key, Long requestedVersion) {
        Optional<SecretPart> localPart = requestedVersion == null
                ? secretPartRepository.findLatest(key)
                : secretPartRepository.findPart(key, requestedVersion);
        localPart.ifPresent(part -> addPart(partsByVersion, part));
    }

    private void addLocalVersionParts(Map<Long, Map<Integer, SecretPart>> partsByVersion, SecretKey key) {
        for (Long version : secretPartRepository.listVersions(key)) {
            secretPartRepository.findPart(key, version)
                    .ifPresent(part -> addPart(partsByVersion, part));
        }
    }

    private void addPart(Map<Long, Map<Integer, SecretPart>> partsByVersion, SecretPart part) {
        if (part == null || part.getVersion() == null || part.getShard() == null) {
            return;
        }
        partsByVersion.computeIfAbsent(part.getVersion(), ignored -> new LinkedHashMap<>())
                .putIfAbsent(part.getPartIndex(), part);
    }

    private List<SecretPart> requireThreshold(Map<Integer, SecretPart> partsByIndex) {
        if (partsByIndex == null || partsByIndex.isEmpty()) {
            throw new SecretNotFoundException();
        }
        int threshold = resolveThreshold(resolveTotalParts());
        if (partsByIndex.size() < threshold) {
            throw new InsufficientShardsException();
        }
        return new TreeSet<>(partsByIndex.keySet()).stream()
                .limit(threshold)
                .map(partsByIndex::get)
                .toList();
    }

    private List<String> resolvePeerUrls() {
        return nodeClient == null ? List.of() : nodeClient.resolvePeerUrls();
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
