package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.PostPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.SecretPartMessage;
import edu.yu.capstone.DistributedSecretsVault.exceptions.DuplicateSecretException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

/**
 * Handles incoming <b>prepare</b> requests for distributed create (POST) operations.
 * <p>
 * When the originator broadcasts a post-prepare, each peer:
 * <ol>
 *   <li>Validates the request</li>
 *   <li>Checks that the secret does <em>not</em> already exist locally (duplicate check)</li>
 *   <li>Buffers the shard in {@link PendingActionsBuffer}</li>
 * </ol>
 * The originator interprets a successful return (no exception) as an ACK.
 *
 * @see PostCommitHandler
 */
@Service
public class PostPrepareHandler {
    private final PendingActionsBuffer pendingActionsBuffer;
    private final SecretPartRepository secretPartRepository;

    public PostPrepareHandler(PendingActionsBuffer pendingActionsBuffer,
            SecretPartRepository secretPartRepository) {
        this.pendingActionsBuffer = pendingActionsBuffer;
        this.secretPartRepository = secretPartRepository;
    }

    /**
     * Processes a post-prepare request: verifies uniqueness and buffers the shard.
     *
     * @param request the prepare request from the originator
     * @throws IllegalArgumentException if the request is invalid
     * @throws DuplicateSecretException if the secret already exists locally
     */
    public void handle(PostPrepareRequest request) {
        validateRequest(request);

        SecretPartMessage message = request.getSecretPartMessage();
        if (secretPartRepository.exists(message.getKey())) {
            throw new DuplicateSecretException();
        }

        SecretPart part = new SecretPart(
                message.getKey(),
                message.getVersion(),
                message.getPartIndex(),
                message.getShard());
        pendingActionsBuffer.bufferAction(
                request.getOperationId(), message.getKey(), ActionType.POST, part);
    }

    /** Validates that all required fields of a post-prepare request are present. */
    private void validateRequest(PostPrepareRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Post prepare request is required");
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
        if (message.getShard() == null) {
            throw new IllegalArgumentException("Secret value is required");
        }
    }
}
