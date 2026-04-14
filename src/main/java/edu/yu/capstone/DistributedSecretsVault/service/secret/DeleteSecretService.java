package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.DeleteSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.util.SecretKeyGenerator;

@Service
public class DeleteSecretService implements SecretCommand<DeleteSecretRequest, Void> {
    private static final String DEFAULT_OWNER_ID = "default";
    private final SecretService secretService;

    public DeleteSecretService(SecretService secretService) {
        this.secretService = secretService;
    }

    @Override
    public ResponseEntity<Void> execute(DeleteSecretRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request is required");
        }
        SecretKey key = SecretKeyGenerator.of(DEFAULT_OWNER_ID, input.getDeleteName());
        secretService.deleteSecret(key);
        return ResponseEntity.noContent().build();
    }
    
    
}
