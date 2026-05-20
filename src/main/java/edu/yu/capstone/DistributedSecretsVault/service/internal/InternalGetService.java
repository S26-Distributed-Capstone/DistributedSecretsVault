package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InsufficientShardsException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.SecretPartResponse;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.SecretPartsResponse;
import edu.yu.capstone.DistributedSecretsVault.service.secret.SecretReconstructionService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

@Service
public class InternalGetService {
    private static final Logger log = LoggerFactory.getLogger(InternalGetService.class);

    private final SecretPartRepository secretPartRepository;
    private final SecretReconstructionService secretReconstructionService;
    private final NodeClient nodeClient;
    private final ClusterConfig clusterConfig;
    private final InternalRepairService internalRepairService;

    public InternalGetService(SecretPartRepository secretPartRepository,
            SecretReconstructionService secretReconstructionService,
            NodeClient nodeClient,
            ClusterConfig clusterConfig,
            InternalRepairService internalRepairService) {
        this.secretPartRepository = secretPartRepository;
        this.secretReconstructionService = secretReconstructionService;
        this.nodeClient = nodeClient;
        this.clusterConfig = clusterConfig;
        this.internalRepairService = internalRepairService;
    }

    public String getAcrossCluster(SecretKey key, Long version) {
        validateKey(key);
        ReconstructionParts reconstructionParts = collectPartsForReconstruction(key, version);
        String value = secretReconstructionService.reconstruct(reconstructionParts.selectedParts());
        maybeRepairLatestRead(key, version, reconstructionParts, value);
        return value;
    }

    public Map<Long, String> getAllVersionsAcrossCluster(SecretKey key) {
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

    public ResponseEntity<SecretPart> getVersion(String user, String secretName, Long version) {
        SecretKey key = validate(user, secretName);
        if (!secretPartRepository.exists(key)) {
            throw new SecretNotFoundException();
        }
        Optional<SecretPart> part = version == null
                ? secretPartRepository.findLatest(key)
                : secretPartRepository.findPart(key, version);
        if (part.isEmpty()) {
            throw new SecretNotFoundException();
        }
        return ResponseEntity.ok(part.get());
    }

    public ResponseEntity<Map<Long, SecretPart>> getAllVersions(String user, String secretName) {
        SecretKey key = validate(user, secretName);
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
        return ResponseEntity.ok(results);
    }

    private SecretKey validate(String user, String secretName) {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        if (secretName == null || secretName.isBlank()) {
            throw new IllegalArgumentException("Secret key is required");
        }
        return new SecretKey(user, secretName);
    }

    private void validateKey(SecretKey key) {
        if (key == null || key.getName() == null || key.getName().isBlank()) {
            throw new IllegalArgumentException("Secret key is required");
        }
    }

    private ReconstructionParts collectPartsForReconstruction(SecretKey key, Long requestedVersion) {
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
        return new ReconstructionParts(requireThreshold(selectedParts), resolvedVersion, selectedParts.size());
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

    private void maybeRepairLatestRead(SecretKey key, Long requestedVersion,
            ReconstructionParts reconstructionParts, String value) {
        if (requestedVersion != null || internalRepairService == null) {
            return;
        }
        if (!internalRepairService.shouldRepairLatestRead(reconstructionParts.availableParts())) {
            return;
        }
        try {
            internalRepairService.repairLatestVersion(key, reconstructionParts.version(), value);
        } catch (RuntimeException e) {
            log.warn("Read repair skipped after successful reconstruction: key={}, version={}, reason={}",
                    key, reconstructionParts.version(), e.getMessage());
        }
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

    private record ReconstructionParts(List<SecretPart> selectedParts, long version, int availableParts) {
    }
}
