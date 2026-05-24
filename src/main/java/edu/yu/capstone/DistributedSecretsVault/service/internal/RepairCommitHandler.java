package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.RepairCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InternalOperationConflictException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer.PendingAction;

/**
 * Handles incoming <b>commit</b> requests for read-repair operations.
 * <p>
 * When the commit message arrives via Kafka, this handler:
 * <ol>
 *   <li>Retrieves and removes the buffered repair action</li>
 *   <li>Verifies the action type and secret key match</li>
 *   <li>Saves the repair shard via {@link SecretPartRepository#savePart}</li>
 * </ol>
 * Repair commits always use {@code savePart} (not {@code updatePart})
 * because the shard may not previously exist on this node.
 *
 * @see RepairPrepareHandler
 */
@Service
public class RepairCommitHandler {
    private final PendingActionsBuffer pendingActionsBuffer;
    private final SecretPartRepository secretPartRepository;

    public RepairCommitHandler(PendingActionsBuffer pendingActionsBuffer,
            SecretPartRepository secretPartRepository) {
        this.pendingActionsBuffer = pendingActionsBuffer;
        this.secretPartRepository = secretPartRepository;
    }

    /**
     * Processes a repair-commit request: finalizes the buffered repair action.
     *
     * @param request the commit request from Kafka
     * @throws IllegalArgumentException         if the request is invalid
     * @throws InternalOperationConflictException if the buffered action is missing or mismatched
     */
    public void handle(RepairCommitRequest request) {
        validateRequest(request);

        PendingAction committed = pendingActionsBuffer.commitAndRemove(request.getOperationId());
        if (committed == null) {
            throw new InternalOperationConflictException(
                    "No staged operation found for repair commit: " + request.getOperationId());
        }
        if (committed.actionType() != ActionType.REPAIR) {
            throw new InternalOperationConflictException(
                    "Staged operation is not a repair: " + request.getOperationId());
        }
        if (!committed.secretKey().equals(request.getSecretKey())) {
            throw new InternalOperationConflictException(
                    "Commit secret key does not match staged repair: " + request.getOperationId());
        }
        SecretPart part = committed.secretPart();
        if (part == null || part.getVersion() == null) {
            throw new InternalOperationConflictException(
                    "No staged shard found for repair commit: " + request.getOperationId());
        }

        secretPartRepository.savePart(part);
    }

    /** Validates that all required fields of a repair-commit request are present. */
    private void validateRequest(RepairCommitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Repair commit request is required");
        }
        if (request.getOperationId() == null) {
            throw new IllegalArgumentException("Operation ID is required");
        }
        if (request.getSecretKey() == null) {
            throw new IllegalArgumentException("Secret key is required");
        }
    }
}
