package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalGetService;

import java.util.Map;

@Service
public class GetSecretService {
    private final InternalGetService internalGetService;

    public GetSecretService(InternalGetService internalGetService) {
        this.internalGetService = internalGetService;
    }

    public ResponseEntity<String> getVersion(String user, String secretName, Long version) {
        SecretKey key = validate(user, secretName);
        String secretValue = internalGetService.getAcrossCluster(key, version);
        return ResponseEntity.ok(secretValue);
    }

    public ResponseEntity<Map<Long, String>> getAllVersions(String user, String secretName) {
        SecretKey key = validate(user, secretName);
        return ResponseEntity.ok(internalGetService.getAllVersionsAcrossCluster(key));
    }

    private SecretKey validate(String user, String secretName) {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        if (secretName == null || secretName.isBlank()) {
            throw new IllegalArgumentException("Secret key is required");
        }
        return new SecretKey(user, secretName);
    }
}
