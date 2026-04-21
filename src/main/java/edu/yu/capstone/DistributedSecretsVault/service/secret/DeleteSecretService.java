package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.DeleteSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.util.SecretKeyGenerator;

@Service
public class DeleteSecretService implements SecretCommand<DeleteSecretRequest, Void> {
    private final SecretService secretService;

    public DeleteSecretService(SecretService secretService) {
        this.secretService = secretService;
    }

    @Override
    public ResponseEntity<Void> execute(DeleteSecretRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (input.getUser() == null || input.getUser().isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        SecretKey key = SecretKeyGenerator.of(input.getUser(), input.getDeleteName());
        secretService.deleteSecret(key);
        return ResponseEntity.noContent().build();
    }
    
    
}
