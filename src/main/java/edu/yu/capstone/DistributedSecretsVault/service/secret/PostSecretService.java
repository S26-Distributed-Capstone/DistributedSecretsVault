package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PostSecretRequest;

@Service
public class PostSecretService implements SecretCommand<PostSecretRequest, String> {
    private final SecretService secretService;

    public PostSecretService(SecretService secretService) {
        this.secretService = secretService;
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
        SecretVersion version = secretService.storeSecret(key, input.getSecretValue());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Secret created (version: " + version.getVersion() + ")");
    }
}
