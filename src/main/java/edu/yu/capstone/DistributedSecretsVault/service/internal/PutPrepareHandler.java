package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PutPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.SecretPartMessage;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

/**
 * Handles incoming <b>prepare</b> requests for distributed update (PUT) operations.
 * <p>
 * When the originator broadcasts a put-prepare, each peer:
 * <ol>
 *   <li>Validates the request</li>
 *   <li>Checks that the secret <em>does</em> exist locally</li>
 *   <li>Buffers the updated shard in {@link PendingActionsBuffer}</li>
 * </ol>
 * The originator interprets a successful return (no exception) as an ACK.
 *
 * @see PutCommitHandler
 */
@Service
public class PutPrepareHandler {
    private final PendingActionsBuffer pendingActionsBuffer;
    private final SecretPartRepository secretPartRepository;

    public PutPrepareHandler(PendingActionsBuffer pendingActionsBuffer,
            SecretPartRepository secretPartRepository) {
        this.pendingActionsBuffer = pendingActionsBuffer;
        this.secretPartRepository = secretPartRepository;
    }

    /**
     * Processes a put-prepare request: verifies existence and buffers the updated shard.
     *
     * @param request the prepare request from the originator
     * @throws IllegalArgumentException if the request is invalid
     * @throws SecretNotFoundException  if the secret does not exist locally
     */
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

    /** Validates that all required fields of a put-prepare request are present. */
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
