package edu.yu.capstone.DistributedSecretsVault.service.internal;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.SecretPartMessage;
import edu.yu.capstone.DistributedSecretsVault.exceptions.DuplicateSecretException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class PostShardService {
    private final SecretPartRepository secretPartRepository;

    public PostShardService(SecretPartRepository secretPartRepository) {
        this.secretPartRepository = secretPartRepository;
    }

    public ResponseEntity<String> postShard(SecretPartMessage input, String user) {
        if (input == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        if (input.getShard() == null) {
            throw new IllegalArgumentException("Secret value is required");
        }
        if (secretPartRepository.exists(input.getKey())) {
            throw new DuplicateSecretException();
        }
        SecretPart part = new SecretPart(input.getKey(), input.getVersion(), input.getPartIndex(), input.getShard());
        secretPartRepository.savePart(part);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Secret created (version: " + part.getVersion() + ")");
    }
}
