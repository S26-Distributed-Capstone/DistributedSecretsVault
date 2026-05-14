package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeleteCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InternalOperationConflictException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer.PendingAction;

/**
 * Handles incoming <b>commit</b> requests on peer nodes.
 * <p>
 * When the originator broadcasts a delete-commit, each peer:
 * <ol>
 *   <li>Looks up the buffered action by {@code operationId}</li>
 *   <li>Executes the actual delete via {@link SecretPartRepository#deleteParts}</li>
 * </ol>
 * Committing an action also removes all other pending actions for the same
 * {@link edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey}
 * (handled by {@link PendingActionsBuffer#commitAndRemove}).
 * <p>
 * If the pending entry has already been evicted or was never buffered,
 * the commit logs a warning but does not fail — the shard may have
 * already been cleaned up.
 */
@Service
public class DeleteCommitHandler {
    private static final Logger log = LoggerFactory.getLogger(DeleteCommitHandler.class);

    private final PendingActionsBuffer pendingActionsBuffer;
    private final SecretPartRepository secretPartRepository;

    public DeleteCommitHandler(PendingActionsBuffer pendingActionsBuffer,
                               SecretPartRepository secretPartRepository) {
        this.pendingActionsBuffer = pendingActionsBuffer;
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

        PendingAction committed = pendingActionsBuffer.commitAndRemove(request.getOperationId());

        if (committed == null) {
            log.warn("Commit received for unknown or expired operationId={}",
                    request.getOperationId());
            throw new InternalOperationConflictException(
                    "No staged operation found for commit: " + request.getOperationId());
        }

        if (!committed.secretKey().equals(request.getSecretKey())) {
            throw new InternalOperationConflictException(
                    "Commit secret key does not match staged operation: " + request.getOperationId());
        }

        if (secretPartRepository.exists(request.getSecretKey())) {
            secretPartRepository.deleteParts(request.getSecretKey());
            log.debug("Delete committed: operationId={}", request.getOperationId());
        } else {
            log.debug("No local shard to delete for operationId={}", request.getOperationId());
        }
    }

    private void validateRequest(DeleteCommitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Delete commit request is required");
        }
        if (request.getOperationId() == null) {
            throw new IllegalArgumentException("Operation ID is required");
        }
        if (request.getSecretKey() == null) {
            throw new IllegalArgumentException("Secret key is required");
        }
    }
}
