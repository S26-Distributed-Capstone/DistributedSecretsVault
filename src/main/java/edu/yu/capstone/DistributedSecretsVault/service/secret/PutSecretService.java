package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.dto.secret.PutSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.util.SecretKeyGenerator;

@Service
public class PutSecretService implements SecretCommand<PutSecretRequest, String> {
    private final SecretService secretService;

    public PutSecretService(SecretService secretService) {
        this.secretService = secretService;
    }

    @Override
    public ResponseEntity<String> execute(PutSecretRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (input.getUser() == null || input.getUser().isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        String currentName = input.getSecretCurrentName();
        String updatedName = input.getSecretUpdatedName();
        if (updatedName != null && !updatedName.isBlank()
                && currentName != null && !currentName.equals(updatedName)) {
            throw new IllegalArgumentException("Renaming secrets is not supported yet");
        }
        String keyName = currentName == null || currentName.isBlank() ? updatedName : currentName;
        SecretKey key = SecretKeyGenerator.of(input.getUser(), keyName);
        SecretVersion version = secretService.updateSecret(key, input.getSecretUpdatedValue());
        return ResponseEntity.ok("Secret updated (version: " + version.getVersion() + ")");
    }

}