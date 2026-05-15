package edu.yu.capstone.DistributedSecretsVault.service.internal;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.QuorumNotReachedException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.communication.CommitPublisher;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.PeerResponse;

/**
 * Orchestrates the distributed delete protocol across the cluster.
 * <p>
 * Protocol:
 * <ol>
 *   <li><b>Prepare</b> - broadcast {@link DeletePrepareRequest} to all peers;
 *       each peer buffers the delete and returns an ACK.</li>
 *   <li><b>ACK collection</b> - count ACKs (peers + self). Require at least
 *       {@code m - k + 1} to proceed (ensures fewer than k shards remain).</li>
 *   <li><b>Commit publication</b> - publish a shard-agnostic commit message to
 *       Kafka. Each node consumes that message and commits its local staged action.</li>
 * </ol>
 */
@Service
public class InternalDeleteService {
    private static final Logger log = LoggerFactory.getLogger(InternalDeleteService.class);

    private final NodeClient nodeClient;
    private final SecretPartRepository secretPartRepository;
    private final PendingActionsBuffer pendingActionsBuffer;
    private final CommitPublisher commitPublisher;
    private final ClusterConfig clusterConfig;
    private final String nodeId;

    public InternalDeleteService(NodeClient nodeClient,
            SecretPartRepository secretPartRepository,
            PendingActionsBuffer pendingActionsBuffer,
            CommitPublisher commitPublisher,
            ClusterConfig clusterConfig) {
        this.nodeClient = nodeClient;
        this.secretPartRepository = secretPartRepository;
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
     * Execute the full distributed delete protocol for the given secret key.
     *
     * @param key the secret to delete across the cluster
     * @throws SecretNotFoundException   if the secret does not exist locally
     * @throws QuorumNotReachedException if insufficient ACKs are received
     */
    public void deleteAcrossCluster(SecretKey key) {
        if (!secretPartRepository.exists(key)) {
            throw new SecretNotFoundException();
        }

        UUID operationId = UUID.randomUUID();
        log.info("Starting distributed delete: operationId={}", operationId);

        List<String> peerUrls = nodeClient.resolvePeerUrls();
        DeletePrepareRequest prepareRequest = new DeletePrepareRequest(nodeId, operationId, key);

        pendingActionsBuffer.bufferAction(operationId, key, ActionType.DELETE);
        int peerAcks = broadcastPrepare(peerUrls, prepareRequest);
        int totalAcks = peerAcks + 1;
        int requiredAcks = computeRequiredAcks();

        log.info("Prepare phase complete: operationId={}, totalAcks={}, requiredAcks={}",
                operationId, totalAcks, requiredAcks);

        if (totalAcks < requiredAcks) {
            log.warn("Quorum not reached for delete: operationId={}, "
                    + "totalAcks={}, requiredAcks={}", operationId, totalAcks, requiredAcks);
            pendingActionsBuffer.discard(operationId);
            throw new QuorumNotReachedException(
                    "Delete failed - received " + totalAcks + " ACKs, required " + requiredAcks);
        }

        try {
            commitPublisher.broadcastCommit(new CommitMessage(operationId, key, ActionType.DELETE));
        } catch (RuntimeException e) {
            pendingActionsBuffer.discard(operationId);
            throw e;
        }

        log.info("Distributed delete commit submitted to Kafka: operationId={}", operationId);
    }

    /**
     * Broadcast delete prepare to all peers and count successful ACKs.
     */
    private int broadcastPrepare(List<String> peerUrls, DeletePrepareRequest request) {
        int acks = 0;
        for (String peerUrl : peerUrls) {
            PeerResponse response = nodeClient.sendDeletePrepare(peerUrl, request);
            if (response.acknowledged()) {
                acks++;
            } else {
                log.debug("Prepare was not acknowledged by peer={}, status={}, error={}",
                        response.peerUrl(), response.statusCode(), response.errorMessage());
            }
        }
        return acks;
    }

    /**
     * Compute the minimum number of ACKs required for a successful delete.
     * <p>
     * Per architecture docs: {@code m - k + 1} where m = quorumM, k = thresholdK.
     * This ensures fewer than k shards remain, making reconstruction impossible.
     *
     * @return the required ACK count (minimum 1)
     */
    private int computeRequiredAcks() {
        int m = clusterConfig.getQuorumM();
        int k = clusterConfig.getThresholdK();
        int required = m - k + 1;
        return Math.max(required, 1);
    }
}
