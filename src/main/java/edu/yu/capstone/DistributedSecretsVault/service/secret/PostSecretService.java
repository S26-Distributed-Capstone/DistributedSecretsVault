package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PostSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalPostService;

/**
 * Public-facing command service for creating a new secret.
 * <p>
 * This layer performs request validation and response formatting while
 * {@link InternalPostService} owns the cluster coordination and shard storage.
 */
@Service
public class PostSecretService implements SecretCommand<PostSecretRequest, String> {
    private static final Logger log = LoggerFactory.getLogger(PostSecretService.class);

    private final InternalPostService internalPostService;

    /**
     * Creates a post command backed by the distributed internal create service.
     *
     * @param internalPostService service that creates secret shards across the cluster
     */
    public PostSecretService(InternalPostService internalPostService) {
        this.internalPostService = internalPostService;
    }

    /**
     * Creates version 1 of a secret for the given user.
     *
     * @param input create request containing user, name, and plaintext value
     * @return HTTP 201 response with the created version number
     * @throws IllegalArgumentException if the request or user is missing
     */
    @Override
    public ResponseEntity<String> execute(PostSecretRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (input.getUser() == null || input.getUser().isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        SecretKey key = new SecretKey(input.getUser(), input.getSecretName());
        log.info("Create secret requested: user={}, secretName={}", key.getOwnerId(), key.getName());
        SecretVersion version = internalPostService.postAcrossCluster(key, input.getSecretValue());
        log.info("Create secret completed: user={}, secretName={}, version={}",
                key.getOwnerId(), key.getName(), version.getVersion());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Secret created (version: " + version.getVersion() + ")");
    }
}
