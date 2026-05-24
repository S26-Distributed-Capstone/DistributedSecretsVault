package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.DeleteSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalDeleteService;

/**
 * Public-facing command service for deleting a secret.
 * <p>
 * The service validates the request, converts it to a {@link SecretKey}, and
 * delegates the distributed delete workflow to {@link InternalDeleteService}.
 */
@Service
public class DeleteSecretService implements SecretCommand<DeleteSecretRequest, Void> {
    private static final Logger log = LoggerFactory.getLogger(DeleteSecretService.class);

    private final InternalDeleteService internalDeleteService;

    /**
     * Creates a delete command backed by the distributed internal delete service.
     *
     * @param internalDeleteService service that deletes secret shards across the cluster
     */
    public DeleteSecretService(InternalDeleteService internalDeleteService) {
        this.internalDeleteService = internalDeleteService;
    }

    /**
     * Deletes all versions and shards for the requested secret.
     *
     * @param input delete request containing user and secret name
     * @return HTTP 204 response when the delete commit has been submitted
     * @throws IllegalArgumentException if the request or user is missing
     */
    @Override
    public ResponseEntity<Void> execute(DeleteSecretRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (input.getUser() == null || input.getUser().isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        SecretKey key = new SecretKey(input.getUser(), input.getDeleteName());
        log.info("Delete secret requested: user={}, secretName={}", key.getOwnerId(), key.getName());
        internalDeleteService.deleteAcrossCluster(key);
        log.info("Delete secret completed: user={}, secretName={}", key.getOwnerId(), key.getName());
        return ResponseEntity.noContent().build();
    }
}
