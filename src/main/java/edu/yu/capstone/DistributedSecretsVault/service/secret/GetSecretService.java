package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import java.util.Map;

@Service
public class GetSecretService {
    private final SecretService secretService;
    private SecretKey key;

    public GetSecretService(SecretService secretService) {
        this.secretService = secretService;
    }

    public ResponseEntity<String> getVersion(String user, String secretName, Long version) {
        validate(user, secretName);
        String secretValue = secretService.getSecret(key, version);
        return ResponseEntity.ok(secretValue);
    }

    public ResponseEntity<Map<Long, String>> getAllVersions(String user, String secretName) {
        validate(user, secretName);
        return ResponseEntity.ok(secretService.getAllVersions(key));
    }

    private void validate(String user, String secretName) {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        if (secretName == null || secretName.isBlank()) {
            throw new IllegalArgumentException("Secret key is required");
        }
        key = new SecretKey(user, secretName);
    }
}