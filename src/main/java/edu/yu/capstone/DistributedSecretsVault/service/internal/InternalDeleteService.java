package edu.yu.capstone.DistributedSecretsVault.service.internal;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.config.ClusterConfig;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeleteCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.QuorumNotReachedException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.NodeClient.PeerResponse;

/**
 * Orchestrates the distributed delete protocol across the cluster.
 * <p>
 * Protocol:
 * <ol>
 *   <li><b>Prepare</b> — broadcast {@link DeletePrepareRequest} to all peers;
 *       each peer buffers the delete and returns an ACK.</li>
 *   <li><b>ACK collection</b> — count ACKs (peers + self). Require at least
 *       {@code m - k + 1} to proceed (ensures fewer than k shards remain).</li>
 *   <li><b>Timing authority</b> — placeholder for future Kafka-based sequencing.
 *       Currently proceeds directly to commit.</li>
 *   <li><b>Commit</b> — broadcast {@link DeleteCommitRequest} to all peers;
 *       each peer executes the buffered delete. Originator deletes locally.</li>
 * </ol>
 */
@Service
public class InternalDeleteService {
    private static final Logger log = LoggerFactory.getLogger(InternalDeleteService.class);

    private final NodeClient nodeClient;
    private final SecretPartRepository secretPartRepository;
    private final ClusterConfig clusterConfig;
    private final String nodeId;

    public InternalDeleteService(NodeClient nodeClient,
                                 SecretPartRepository secretPartRepository,
                                 ClusterConfig clusterConfig) {
        this.nodeClient = nodeClient;
        this.secretPartRepository = secretPartRepository;
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
     * @throws SecretNotFoundException    if the secret does not exist locally
     * @throws QuorumNotReachedException  if insufficient ACKs are received
     */
    public void deleteAcrossCluster(SecretKey key) {
        // Validate local existence
        if (!secretPartRepository.exists(key)) {
            throw new SecretNotFoundException();
        }

        UUID operationId = UUID.randomUUID();
        log.info("Starting distributed delete: operationId={}", operationId);

        // Phase 1: Prepare — broadcast to peers
        List<String> peerUrls = nodeClient.resolvePeerUrls();
        DeletePrepareRequest prepareRequest = new DeletePrepareRequest(nodeId, operationId, key);

        int peerAcks = broadcastPrepare(peerUrls, prepareRequest);
        int totalAcks = peerAcks + 1; // +1 for the originator itself
        int requiredAcks = computeRequiredAcks();

        log.info("Prepare phase complete: operationId={}, totalAcks={}, requiredAcks={}",
                operationId, totalAcks, requiredAcks);

        // Phase 2: Check threshold
        if (totalAcks < requiredAcks) {
            log.warn("Quorum not reached for delete: operationId={}, "
                    + "totalAcks={}, requiredAcks={}", operationId, totalAcks, requiredAcks);
            throw new QuorumNotReachedException(
                    "Delete failed — received " + totalAcks + " ACKs, required " + requiredAcks);
        }

        // Phase 3: Submit to central timing authority (future Kafka integration)
        submitToTimingAuthority(operationId, key);

        // Phase 4: Commit — broadcast to peers and delete locally
        DeleteCommitRequest commitRequest = new DeleteCommitRequest(operationId, key);
        broadcastCommit(peerUrls, commitRequest);

        // Delete local shard
        secretPartRepository.deleteParts(key);
        log.info("Distributed delete complete: operationId={}", operationId);
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
     * Broadcast delete commit to all peers. Commit failures are logged
     * but do not fail the operation — the prepare quorum already guarantees
     * enough shards will be deleted.
     */
    private void broadcastCommit(List<String> peerUrls, DeleteCommitRequest request) {
        for (String peerUrl : peerUrls) {
            PeerResponse response = nodeClient.sendDeleteCommit(peerUrl, request);
            if (!response.acknowledged()) {
                log.warn("Commit delivery failed to peer {} for operationId={}, status={}",
                        response.peerUrl(), request.getOperationId(), response.statusCode());
            }
        }
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
        return Math.max(required, 1); // always require at least 1
    }

    /**
     * Placeholder for future Kafka-based central timing authority integration.
     * <p>
     * In the future, this method will submit the delete operation to Kafka,
     * which will assign a global sequence number and broadcast the commit
     * to all nodes. For now, the originator proceeds directly to commit.
     *
     * @param operationId the unique operation identifier
     * @param key         the secret key being deleted
     */
    private void submitToTimingAuthority(UUID operationId, SecretKey key) {
        // TODO: Replace with Kafka producer call when CTA is implemented.
        // The Kafka message would contain operationId + secretKey, and
        // all nodes (including this one) would consume the commit from Kafka
        // instead of receiving it via direct HTTP.
        log.debug("Timing authority placeholder: operationId={} (proceeding directly to commit)",
                operationId);
    }
}
