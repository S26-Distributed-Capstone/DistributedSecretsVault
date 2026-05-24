package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.dto.secret.PutSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalPutService;

/**
 * Public-facing command service for updating an existing secret.
 * <p>
 * The service validates the request, builds the secret key, and delegates
 * version creation and cluster coordination to {@link InternalPutService}.
 */
@Service
public class PutSecretService implements SecretCommand<PutSecretRequest, String> {
    private static final Logger log = LoggerFactory.getLogger(PutSecretService.class);

    private final InternalPutService internalPutService;

    /**
     * Creates an update command backed by the distributed internal put service.
     *
     * @param internalPutService service that writes the next secret version across the cluster
     */
    public PutSecretService(InternalPutService internalPutService) {
        this.internalPutService = internalPutService;
    }

    /**
     * Stores a new version for an existing secret.
     *
     * @param input update request containing user, secret name, and new plaintext value
     * @return HTTP 200 response with the new version number
     * @throws IllegalArgumentException if the request or user is missing
     */
    @Override
    public ResponseEntity<String> execute(PutSecretRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (input.getUser() == null || input.getUser().isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        SecretKey key = new SecretKey(input.getUser(), input.getSecretCurrentName());
        log.info("Update secret requested: user={}, secretName={}", key.getOwnerId(), key.getName());
        SecretVersion version = internalPutService.putAcrossCluster(key, input.getSecretUpdatedValue());
        log.info("Update secret completed: user={}, secretName={}, version={}",
                key.getOwnerId(), key.getName(), version.getVersion());
        return ResponseEntity.ok("Secret updated (version: " + version.getVersion() + ")");
    }
}
