package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.util.SecretKeyGenerator;

@Service
public class GetSecretService implements SecretQuery<String, String>  {
    private static final String DEFAULT_OWNER_ID = "default";
    private final SecretService secretService;

    public GetSecretService(SecretService secretService) {
        this.secretService = secretService;
    }

    @Override
    public ResponseEntity<String> execute(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Secret key is required");
        }
        SecretKey key = SecretKeyGenerator.of(DEFAULT_OWNER_ID, input);
        String secretValue = secretService.getSecret(key, null);
        return ResponseEntity.ok(secretValue);
    }
    
}
