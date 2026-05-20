package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalGetService;

import java.util.Map;

@Service
public class GetSecretService {
    private static final Logger log = LoggerFactory.getLogger(GetSecretService.class);

    private final InternalGetService internalGetService;

    public GetSecretService(InternalGetService internalGetService) {
        this.internalGetService = internalGetService;
    }

    public ResponseEntity<String> getVersion(String user, String secretName, Long version) {
        SecretKey key = validate(user, secretName);
        log.info("Retrieve secret requested: user={}, secretName={}, version={}",
                key.getOwnerId(), key.getName(), version == null ? "latest" : version);
        String secretValue = internalGetService.getAcrossCluster(key, version);
        log.info("Retrieve secret completed: user={}, secretName={}, version={}",
                key.getOwnerId(), key.getName(), version == null ? "latest" : version);
        return ResponseEntity.ok(secretValue);
    }

    public ResponseEntity<Map<Long, String>> getAllVersions(String user, String secretName) {
        SecretKey key = validate(user, secretName);
        log.info("Retrieve all secret versions requested: user={}, secretName={}",
                key.getOwnerId(), key.getName());
        Map<Long, String> versions = internalGetService.getAllVersionsAcrossCluster(key);
        log.info("Retrieve all secret versions completed: user={}, secretName={}, versionCount={}",
                key.getOwnerId(), key.getName(), versions.size());
        return ResponseEntity.ok(versions);
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
