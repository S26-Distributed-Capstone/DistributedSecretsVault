package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

/**
 * Handles incoming <b>prepare</b> requests on peer nodes.
 * <p>
 * When the originator broadcasts a delete-prepare, each peer:
 * <ol>
 * <li>Validates the request</li>
 * <li>Checks that the secret exists locally</li>
 * <li>Buffers the delete in {@link PendingActionsBuffer}</li>
 * </ol>
 */
@Service
public class DeletePrepareHandler {
    private static final Logger log = LoggerFactory.getLogger(DeletePrepareHandler.class);

    private final PendingActionsBuffer pendingActionsBuffer;
    private final SecretPartRepository secretPartRepository;

    public DeletePrepareHandler(PendingActionsBuffer pendingActionsBuffer,
            SecretPartRepository secretPartRepository) {
        this.pendingActionsBuffer = pendingActionsBuffer;
        this.secretPartRepository = secretPartRepository;
    }

    /**
     * Handle an incoming delete prepare request.
     *
     * @param request the prepare request from the originator
     * @throws IllegalArgumentException if the request is invalid
     * @throws SecretNotFoundException  if the secret does not exist locally
     */
    public void handle(DeletePrepareRequest request) {
        validateRequest(request);

        boolean exists = secretPartRepository.exists(request.getSecretKey());
        if (!exists) {
            log.warn("Delete prepare received for non-existent secret: operationId={}, secretKey={}",
                    request.getOperationId(), request.getSecretKey());
            throw new SecretNotFoundException("Secret not found: " + request.getSecretKey());
        }
        pendingActionsBuffer.bufferAction(
                request.getOperationId(), request.getSecretKey(), ActionType.DELETE);
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
