package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PostSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.util.SecretKeyGenerator;

@Service
public class PostSecretService implements SecretCommand<PostSecretRequest, String> {
    private static final String DEFAULT_OWNER_ID = "default";
    private final SecretService secretService;

    public PostSecretService(SecretService secretService) {
        this.secretService = secretService;
    }

    @Override
    public ResponseEntity<String> execute(PostSecretRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request is required");
        }
        SecretKey key = SecretKeyGenerator.of(DEFAULT_OWNER_ID, input.getSecretName());
        SecretVersion version = secretService.storeSecret(key, input.getSecretValue());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Secret created (version: " + version.getVersion() + ")");
    }

}
