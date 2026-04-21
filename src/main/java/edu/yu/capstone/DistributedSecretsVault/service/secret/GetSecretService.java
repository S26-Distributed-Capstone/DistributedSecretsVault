package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.util.SecretKeyGenerator;

@Service
public class GetSecretService {
    private final SecretService secretService;

    public GetSecretService(SecretService secretService) {
        this.secretService = secretService;
    }

    public ResponseEntity<String> execute(String user, String secretName) {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        if (secretName == null || secretName.isBlank()) {
            throw new IllegalArgumentException("Secret key is required");
        }
        SecretKey key = SecretKeyGenerator.of(user, secretName);
        String secretValue = secretService.getSecret(key, null);
        return ResponseEntity.ok(secretValue);
    }
    
}
