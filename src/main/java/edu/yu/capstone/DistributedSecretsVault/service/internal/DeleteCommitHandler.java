package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeleteCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

/**
 * Handles incoming <b>commit</b> requests on peer nodes.
 * <p>
 * When the originator broadcasts a delete-commit, each peer:
 * <ol>
 *   <li>Looks up the buffered delete by {@code operationId}</li>
 *   <li>Executes the actual delete via {@link SecretPartRepository#deleteParts}</li>
 * </ol>
 * If the pending entry has already been evicted or was never buffered,
 * the commit logs a warning but does not fail — the shard may have
 * already been cleaned up.
 */
@Service
public class DeleteCommitHandler {
    private static final Logger log = LoggerFactory.getLogger(DeleteCommitHandler.class);

    private final PendingDeleteBuffer pendingDeleteBuffer;
    private final SecretPartRepository secretPartRepository;

    public DeleteCommitHandler(PendingDeleteBuffer pendingDeleteBuffer,
                               SecretPartRepository secretPartRepository) {
        this.pendingDeleteBuffer = pendingDeleteBuffer;
        this.secretPartRepository = secretPartRepository;
    }

    /**
     * Handle an incoming delete commit request.
     *
     * @param request the commit request from the originator
     * @throws IllegalArgumentException if the request is invalid
     */
    public void handle(DeleteCommitRequest request) {
        validateRequest(request);

        DeletePrepareRequest buffered = pendingDeleteBuffer.getAndRemove(request.getOperationId());

        if (buffered == null) {
            log.warn("Commit received for unknown/expired operationId={}, "
                    + "attempting delete anyway for secretKey={}",
                    request.getOperationId(), request.getSecretKey());
        }

        // Use the secret key from the commit request (authoritative).
        if (secretPartRepository.exists(request.getSecretKey())) {
            secretPartRepository.deleteParts(request.getSecretKey());
            log.info("Delete committed: operationId={}, secretKey={}",
                    request.getOperationId(), request.getSecretKey());
        } else {
            log.debug("No local shard to delete for operationId={}, secretKey={}",
                    request.getOperationId(), request.getSecretKey());
        }
    }

    private void validateRequest(DeleteCommitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Delete commit request is required");
        }
        if (request.getOperationId() == null || request.getOperationId().isBlank()) {
            throw new IllegalArgumentException("Operation ID is required");
        }
        if (request.getSecretKey() == null) {
            throw new IllegalArgumentException("Secret key is required");
        }
    }
}
