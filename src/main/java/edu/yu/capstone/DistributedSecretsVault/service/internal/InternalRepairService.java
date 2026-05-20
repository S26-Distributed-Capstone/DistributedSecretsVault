package edu.yu.capstone.DistributedSecretsVault.service.internal;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.RepairPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.SecretPartMessage;
import edu.yu.capstone.DistributedSecretsVault.service.communication.CommitPublisher;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.PeerResponse;
import edu.yu.capstone.DistributedSecretsVault.service.secret.SecretSharingService;

@Service
public class InternalRepairService {
    private static final Logger log = LoggerFactory.getLogger(InternalRepairService.class);

    private final NodeClient nodeClient;
    private final SecretSharingService secretSharingService;
    private final PendingActionsBuffer pendingActionsBuffer;
    private final CommitPublisher commitPublisher;
    private final ClusterConfig clusterConfig;
    private final String nodeId;

    public InternalRepairService(NodeClient nodeClient,
            SecretSharingService secretSharingService,
            PendingActionsBuffer pendingActionsBuffer,
            CommitPublisher commitPublisher,
            ClusterConfig clusterConfig) {
        this.nodeClient = nodeClient;
        this.secretSharingService = secretSharingService;
        this.pendingActionsBuffer = pendingActionsBuffer;
        this.commitPublisher = commitPublisher;
        this.clusterConfig = clusterConfig;

        String envNodeId = System.getenv("NODE_NAME");
        if (envNodeId == null || envNodeId.isBlank()) {
            envNodeId = System.getProperty("NODE_NAME");
        }
        this.nodeId = (envNodeId != null && !envNodeId.isBlank())
                ? envNodeId : "local-node";
    }

    public boolean shouldRepairLatestRead(int availableParts) {
        if (clusterConfig == null || !clusterConfig.isRepairEnabled()) {
            return false;
        }
        int totalParts = resolveTotalParts();
        int threshold = resolveThreshold(totalParts);
        if (totalParts <= threshold || availableParts < threshold) {
            return false;
        }
        int buffer = Math.max(clusterConfig.getRepairTriggerBuffer(), 0);
        return availableParts <= threshold + buffer;
    }

    public void repairLatestVersion(SecretKey key, long version, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Secret key and value are required for repair");
        }

        UUID operationId = UUID.randomUUID();
        List<SecretPart> parts = createParts(key, value, version);
        SecretPart localPart = parts.get(0);
        List<SecretPart> peerParts = parts.subList(1, parts.size());
        List<String> peerUrls = nodeClient.resolvePeerUrls();

        log.info("Starting read repair: operationId={}, key={}, version={}", operationId, key, version);
        pendingActionsBuffer.bufferAction(operationId, key, ActionType.REPAIR, localPart);

        int peerAcks = broadcastPrepare(peerUrls, peerParts, operationId);
        int totalAcks = peerAcks + 1;
        int requiredAcks = computeRequiredAcks();
        if (totalAcks < requiredAcks) {
            pendingActionsBuffer.discard(operationId);
            log.warn("Read repair skipped because quorum was not reached: operationId={}, totalAcks={}, requiredAcks={}",
                    operationId, totalAcks, requiredAcks);
            return;
        }

        try {
            commitPublisher.broadcastCommit(new CommitMessage(operationId, key, ActionType.REPAIR));
            log.info("Read repair commit submitted to Kafka: operationId={}, version={}", operationId, version);
        } catch (RuntimeException e) {
            pendingActionsBuffer.discard(operationId);
            log.warn("Read repair skipped because commit publish failed: operationId={}, reason={}",
                    operationId, e.getMessage());
        }
    }

    private int broadcastPrepare(List<String> peerUrls, List<SecretPart> peerParts, UUID operationId) {
        int acks = 0;
        int count = Math.min(peerUrls.size(), peerParts.size());
        for (int i = 0; i < count; i++) {
            SecretPart part = peerParts.get(i);
            SecretPartMessage message = new SecretPartMessage(
                    part.getKey(),
                    part.getVersion(),
                    part.getShard(),
                    System.currentTimeMillis(),
                    part.getPartIndex());
            RepairPrepareRequest request = new RepairPrepareRequest(nodeId, operationId, message);
            PeerResponse response = nodeClient.sendRepairPrepare(peerUrls.get(i), request);
            if (response.acknowledged()) {
                acks++;
            } else {
                log.debug("Repair prepare was not acknowledged by peer={}, status={}, error={}",
                        response.peerUrl(), response.statusCode(), response.errorMessage());
            }
        }
        return acks;
    }

    private List<SecretPart> createParts(SecretKey key, String value, long version) {
        int totalParts = resolveTotalParts();
        int threshold = resolveThreshold(totalParts);
        return secretSharingService.split(key, value, threshold, totalParts).stream()
                .peek(part -> part.setVersion(version))
                .sorted(Comparator.comparingInt(SecretPart::getPartIndex))
                .toList();
    }

    private int computeRequiredAcks() {
        int required = clusterConfig == null ? 0 : clusterConfig.getQuorumM();
        return Math.max(required, 1);
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
