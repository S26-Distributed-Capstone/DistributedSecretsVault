package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.exceptions.SecretNotFoundException;
import edu.yu.capstone.DistributedSecretsVault.repository.SecretPartRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class InternalGetService {
    private final SecretPartRepository secretPartRepository;

    public InternalGetService(SecretPartRepository secretPartRepository) {
        this.secretPartRepository = secretPartRepository;
    }

    public ResponseEntity<SecretPart> getVersion(String user, String secretName, Long version) {
        SecretKey key = validate(user, secretName);
        if (!secretPartRepository.exists(key)) {
            throw new SecretNotFoundException();
        }
        Optional<SecretPart> part = version == null
                ? secretPartRepository.findLatest(key)
                : secretPartRepository.findPart(key, version);
        if (part.isEmpty()) {
            throw new SecretNotFoundException();
        }
        return ResponseEntity.ok(part.get());
    }

    public ResponseEntity<Map<Long, SecretPart>> getAllVersions(String user, String secretName) {
        SecretKey key = validate(user, secretName);
        List<Long> versions = secretPartRepository.listVersions(key);
        if (versions.isEmpty()) {
            throw new SecretNotFoundException();
        }
        Map<Long, SecretPart> results = new LinkedHashMap<>();
        versions.stream().sorted().forEach(version -> {
            Optional<SecretPart> part = secretPartRepository.findPart(key, version);
            if (part.isEmpty()) {
                throw new SecretNotFoundException();
            }
            results.put(version, part.get());
        });
        return ResponseEntity.ok(results);
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
