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
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PostPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.SecretPartMessage;
import edu.yu.capstone.DistributedSecretsVault.exceptions.DuplicateSecretException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.QuorumNotReachedException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.communication.CommitPublisher;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.PeerResponse;
import edu.yu.capstone.DistributedSecretsVault.service.secret.SecretSharingService;

/**
 * Orchestrates the distributed two-phase commit for creating a new secret.
 * <p>
 * Flow:
 * <ol>
 *   <li>Split the plaintext into Shamir shards</li>
 *   <li>Buffer the local shard and broadcast prepare requests to peers</li>
 *   <li>If quorum is reached, publish a commit message via Kafka</li>
 *   <li>If quorum fails, discard the buffered action</li>
 * </ol>
 *
 * @see PostPrepareHandler
 * @see PostCommitHandler
 */
@Service
public class InternalPostService {
    private static final Logger log = LoggerFactory.getLogger(InternalPostService.class);

    private final NodeClient nodeClient;
    private final SecretPartRepository secretPartRepository;
    private final SecretSharingService secretSharingService;
    private final PendingActionsBuffer pendingActionsBuffer;
    private final CommitPublisher commitPublisher;
    private final ClusterConfig clusterConfig;
    private final String nodeId;

    public InternalPostService(NodeClient nodeClient,
            SecretPartRepository secretPartRepository,
            SecretSharingService secretSharingService,
            PendingActionsBuffer pendingActionsBuffer,
            CommitPublisher commitPublisher,
            ClusterConfig clusterConfig) {
        this.nodeClient = nodeClient;
        this.secretPartRepository = secretPartRepository;
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

    /**
     * Creates a secret across the cluster using the two-phase commit protocol.
     *
     * @param key   composite key identifying the secret
     * @param value plaintext secret value to store
     * @return the {@link SecretVersion} created (always version 1)
     * @throws DuplicateSecretException    if the secret already exists locally
     * @throws QuorumNotReachedException   if not enough peers acknowledged the prepare
     * @throws IllegalArgumentException    if key or value is null/blank
     */
    public SecretVersion postAcrossCluster(SecretKey key, String value) {
        validateInput(key, value);
        if (secretPartRepository.exists(key)) {
            throw new DuplicateSecretException();
        }

        UUID operationId = UUID.randomUUID();
        long version = 1L;
        log.info("Starting distributed post: operationId={}, key={}, version={}", operationId, key, version);
        List<SecretPart> parts = createParts(key, value, version);
        SecretPart localPart = parts.get(0);
        List<SecretPart> peerParts = parts.subList(1, parts.size());
        List<String> peerUrls = nodeClient.resolvePeerUrls();

        pendingActionsBuffer.bufferAction(operationId, key, ActionType.POST, localPart);
        int peerAcks = broadcastPrepare(peerUrls, peerParts, operationId);
        int totalAcks = peerAcks + 1;
        int requiredAcks = computeRequiredAcks();
        log.info("Post prepare phase complete: operationId={}, totalAcks={}, requiredAcks={}",
                operationId, totalAcks, requiredAcks);
        if (totalAcks < requiredAcks) {
            log.warn("Quorum not reached for post: operationId={}, totalAcks={}, requiredAcks={}",
                    operationId, totalAcks, requiredAcks);
            pendingActionsBuffer.discard(operationId);
            throw new QuorumNotReachedException(
                    "Post failed - received " + totalAcks + " ACKs, required " + requiredAcks);
        }

        try {
            commitPublisher.broadcastCommit(new CommitMessage(operationId, key, ActionType.POST));
        } catch (RuntimeException e) {
            pendingActionsBuffer.discard(operationId);
            throw e;
        }
        log.info("Distributed post commit submitted to Kafka: operationId={}", operationId);
        return new SecretVersion(key, version, System.currentTimeMillis());
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
            PostPrepareRequest request = new PostPrepareRequest(nodeId, operationId, message);
            PeerResponse response = nodeClient.sendPostPrepare(peerUrls.get(i), request);
            if (response.acknowledged()) {
                acks++;
            } else {
                log.debug("Post prepare was not acknowledged by peer={}, status={}, error={}",
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

    private void validateInput(SecretKey key, String value) {
        if (key == null || key.getName() == null || key.getName().isBlank()) {
            throw new IllegalArgumentException("Secret key is required");
        }
        if (value == null) {
            throw new IllegalArgumentException("Secret value is required");
        }
    }
}
