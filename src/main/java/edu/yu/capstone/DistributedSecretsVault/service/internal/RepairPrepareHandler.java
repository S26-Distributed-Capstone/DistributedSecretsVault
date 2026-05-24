package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.RepairPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.SecretPartMessage;

/**
 * Handles incoming <b>prepare</b> requests for read-repair operations.
 * <p>
 * Unlike POST/PUT, repair does not check for existence — the shard may
 * not exist on this node (that's the whole point of the repair). The handler
 * simply buffers the shard in {@link PendingActionsBuffer} for later commit.
 *
 * @see RepairCommitHandler
 * @see InternalRepairService
 */
@Service
public class RepairPrepareHandler {
    private final PendingActionsBuffer pendingActionsBuffer;

    public RepairPrepareHandler(PendingActionsBuffer pendingActionsBuffer) {
        this.pendingActionsBuffer = pendingActionsBuffer;
    }

    /**
     * Processes a repair-prepare request: buffers the shard without existence checks.
     *
     * @param request the repair prepare request from the originator
     * @throws IllegalArgumentException if the request is invalid
     */
    public void handle(RepairPrepareRequest request) {
        validateRequest(request);

        SecretPartMessage message = request.getSecretPartMessage();
        SecretPart part = new SecretPart(
                message.getKey(),
                message.getVersion(),
                message.getPartIndex(),
                message.getShard());
        pendingActionsBuffer.bufferAction(
                request.getOperationId(), message.getKey(), ActionType.REPAIR, part);
    }

    /** Validates that all required fields of a repair-prepare request are present. */
    private void validateRequest(RepairPrepareRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Repair prepare request is required");
        }
        if (request.getOperationId() == null) {
            throw new IllegalArgumentException("Operation ID is required");
        }
        SecretPartMessage message = request.getSecretPartMessage();
        if (message == null) {
            throw new IllegalArgumentException("Secret part message is required");
        }
        if (message.getKey() == null) {
            throw new IllegalArgumentException("Secret key is required");
        }
        if (message.getVersion() == null) {
            throw new IllegalArgumentException("Secret version is required");
        }
        if (message.getShard() == null) {
            throw new IllegalArgumentException("Secret value is required");
        }
    }
}
