package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

/**
 * Handles incoming <b>prepare</b> requests on peer nodes.
 * <p>
 * When the originator broadcasts a delete-prepare, each peer:
 * <ol>
 *   <li>Validates the request</li>
 *   <li>Checks that the secret exists locally</li>
 *   <li>Buffers the delete in {@link PendingDeleteBuffer}</li>
 * </ol>
 * If the secret does not exist locally, the prepare still succeeds —
 * the node simply has no shard to delete, which is a valid state.
 */
@Service
public class DeletePrepareHandler {
    private static final Logger log = LoggerFactory.getLogger(DeletePrepareHandler.class);

    private final PendingDeleteBuffer pendingDeleteBuffer;
    private final SecretPartRepository secretPartRepository;

    public DeletePrepareHandler(PendingDeleteBuffer pendingDeleteBuffer,
                                SecretPartRepository secretPartRepository) {
        this.pendingDeleteBuffer = pendingDeleteBuffer;
        this.secretPartRepository = secretPartRepository;
    }

    /**
     * Handle an incoming delete prepare request.
     *
     * @param request the prepare request from the originator
     * @throws IllegalArgumentException if the request is invalid
     */
    public void handle(DeletePrepareRequest request) {
        validateRequest(request);

        boolean exists = secretPartRepository.exists(request.getSecretKey());
        log.info("Delete prepare received: operationId={}, secretKey={}, shardExists={}",
                request.getOperationId(), request.getSecretKey(), exists);

        // Buffer the delete regardless of whether we have a local shard.
        // On commit, we'll only delete if there's something to delete.
        pendingDeleteBuffer.bufferDelete(request);
    }

    private void validateRequest(DeletePrepareRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Delete prepare request is required");
        }
        if (request.getOperationId() == null || request.getOperationId().isBlank()) {
            throw new IllegalArgumentException("Operation ID is required");
        }
        if (request.getSecretKey() == null) {
            throw new IllegalArgumentException("Secret key is required");
        }
    }
}
