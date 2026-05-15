package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PutPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.SecretPartMessage;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

@Service
public class PutPrepareHandler {
    private final PendingActionsBuffer pendingActionsBuffer;
    private final SecretPartRepository secretPartRepository;

    public PutPrepareHandler(PendingActionsBuffer pendingActionsBuffer,
            SecretPartRepository secretPartRepository) {
        this.pendingActionsBuffer = pendingActionsBuffer;
        this.secretPartRepository = secretPartRepository;
    }

    public void handle(PutPrepareRequest request) {
        validateRequest(request);

        SecretPartMessage message = request.getSecretPartMessage();
        if (!secretPartRepository.exists(message.getKey())) {
            throw new SecretNotFoundException();
        }

        SecretPart part = new SecretPart(
                message.getKey(),
                message.getVersion(),
                message.getPartIndex(),
                message.getShard());
        pendingActionsBuffer.bufferAction(
                request.getOperationId(), message.getKey(), ActionType.PUT, part);
    }

    private void validateRequest(PutPrepareRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Put prepare request is required");
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
