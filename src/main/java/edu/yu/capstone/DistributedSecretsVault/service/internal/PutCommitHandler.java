package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.PutCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.InternalOperationConflictException;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer.PendingAction;

@Service
public class PutCommitHandler {
    private final PendingActionsBuffer pendingActionsBuffer;
    private final SecretPartRepository secretPartRepository;

    public PutCommitHandler(PendingActionsBuffer pendingActionsBuffer,
            SecretPartRepository secretPartRepository) {
        this.pendingActionsBuffer = pendingActionsBuffer;
        this.secretPartRepository = secretPartRepository;
    }

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
