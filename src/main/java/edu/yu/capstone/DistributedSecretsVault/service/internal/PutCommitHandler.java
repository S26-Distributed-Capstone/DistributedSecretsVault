package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.PutCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InternalOperationConflictException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer.PendingAction;

/**
 * Handles incoming <b>commit</b> requests for distributed update (PUT) operations.
 * <p>
 * When the commit message arrives via Kafka, this handler:
 * <ol>
 *   <li>Retrieves and removes the buffered prepare action</li>
 *   <li>Verifies the action type and secret key match</li>
 *   <li>Updates the shard via {@link SecretPartRepository#updatePart}</li>
 * </ol>
 *
 * @see PutPrepareHandler
 */
@Service
public class PutCommitHandler {
    private final PendingActionsBuffer pendingActionsBuffer;
    private final SecretPartRepository secretPartRepository;

    public PutCommitHandler(PendingActionsBuffer pendingActionsBuffer,
            SecretPartRepository secretPartRepository) {
        this.pendingActionsBuffer = pendingActionsBuffer;
        this.secretPartRepository = secretPartRepository;
    }

    /**
     * Processes a put-commit request: finalizes the buffered update action.
     *
     * @param request the commit request from Kafka
     * @throws IllegalArgumentException         if the request is invalid
     * @throws InternalOperationConflictException if the buffered action is missing or mismatched
     * @throws SecretNotFoundException           if the secret no longer exists
     */
    public void handle(PutCommitRequest request) {
        validateRequest(request);

        PendingAction committed = pendingActionsBuffer.commitAndRemove(request.getOperationId());
        if (committed == null) {
            throw new InternalOperationConflictException(
                    "No staged operation found for commit: " + request.getOperationId());
        }
        if (committed.actionType() != ActionType.PUT) {
            throw new InternalOperationConflictException(
                    "Staged operation is not a put: " + request.getOperationId());
        }
        if (!committed.secretKey().equals(request.getSecretKey())) {
            throw new InternalOperationConflictException(
                    "Commit secret key does not match staged operation: " + request.getOperationId());
        }
        if (committed.secretPart() == null) {
            throw new InternalOperationConflictException(
                    "No staged shard found for put commit: " + request.getOperationId());
        }

        if (!secretPartRepository.updatePart(committed.secretPart())) {
            throw new SecretNotFoundException();
        }
    }

    /** Validates that all required fields of a put-commit request are present. */
    private void validateRequest(PutCommitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Put commit request is required");
        }
        if (request.getOperationId() == null) {
            throw new IllegalArgumentException("Operation ID is required");
        }
        if (request.getSecretKey() == null) {
            throw new IllegalArgumentException("Secret key is required");
        }
    }
}
