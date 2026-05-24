package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.PostCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.DuplicateSecretException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InternalOperationConflictException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer.PendingAction;

/**
 * Handles incoming <b>commit</b> requests for distributed create (POST) operations.
 * <p>
 * When the commit message arrives via Kafka, this handler:
 * <ol>
 *   <li>Retrieves and removes the buffered prepare action</li>
 *   <li>Verifies the action type and secret key match</li>
 *   <li>Re-checks uniqueness (idempotency guard)</li>
 *   <li>Persists the shard via {@link SecretPartRepository#savePart}</li>
 * </ol>
 *
 * @see PostPrepareHandler
 */
@Service
public class PostCommitHandler {
    private final PendingActionsBuffer pendingActionsBuffer;
    private final SecretPartRepository secretPartRepository;

    public PostCommitHandler(PendingActionsBuffer pendingActionsBuffer,
            SecretPartRepository secretPartRepository) {
        this.pendingActionsBuffer = pendingActionsBuffer;
        this.secretPartRepository = secretPartRepository;
    }

    /**
     * Processes a post-commit request: finalizes the buffered create action.
     *
     * @param request the commit request from Kafka
     * @throws IllegalArgumentException         if the request is invalid
     * @throws InternalOperationConflictException if the buffered action is missing or mismatched
     * @throws DuplicateSecretException          if the secret now exists (concurrent create)
     */
    public void handle(PostCommitRequest request) {
        validateRequest(request);

        PendingAction committed = pendingActionsBuffer.commitAndRemove(request.getOperationId());
        if (committed == null) {
            throw new InternalOperationConflictException(
                    "No staged operation found for commit: " + request.getOperationId());
        }
        if (committed.actionType() != ActionType.POST) {
            throw new InternalOperationConflictException(
                    "Staged operation is not a post: " + request.getOperationId());
        }
        if (!committed.secretKey().equals(request.getSecretKey())) {
            throw new InternalOperationConflictException(
                    "Commit secret key does not match staged operation: " + request.getOperationId());
        }
        if (committed.secretPart() == null) {
            throw new InternalOperationConflictException(
                    "No staged shard found for post commit: " + request.getOperationId());
        }
        if (secretPartRepository.exists(request.getSecretKey())) {
            throw new DuplicateSecretException();
        }

        secretPartRepository.savePart(committed.secretPart());
    }

    /** Validates that all required fields of a post-commit request are present. */
    private void validateRequest(PostCommitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Post commit request is required");
        }
        if (request.getOperationId() == null) {
            throw new IllegalArgumentException("Operation ID is required");
        }
        if (request.getSecretKey() == null) {
            throw new IllegalArgumentException("Secret key is required");
        }
    }
}
