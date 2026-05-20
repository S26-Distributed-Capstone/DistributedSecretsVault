package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.RepairPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.SecretPartMessage;

@Service
public class RepairPrepareHandler {
    private final PendingActionsBuffer pendingActionsBuffer;

    public RepairPrepareHandler(PendingActionsBuffer pendingActionsBuffer) {
        this.pendingActionsBuffer = pendingActionsBuffer;
    }

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
