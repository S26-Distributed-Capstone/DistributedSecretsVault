package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PostSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalPostService;

@Service
public class PostSecretService implements SecretCommand<PostSecretRequest, String> {
    private final InternalPostService internalPostService;

    public PostSecretService(InternalPostService internalPostService) {
        this.internalPostService = internalPostService;
    }

    @Override
    public ResponseEntity<String> execute(PostSecretRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (input.getUser() == null || input.getUser().isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        SecretKey key = new SecretKey(input.getUser(), input.getSecretName());
        SecretVersion version = internalPostService.postAcrossCluster(key, input.getSecretValue());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Secret created (version: " + version.getVersion() + ")");
    }
}
