package edu.yu.capstone.DistributedSecretsVault.service.internal;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;

import java.util.Map;

@Service
public class GetShardService {
    private final ShardService shardService;
    private SecretKey key;

    public GetShardService(ShardService shardService) {
        this.shardService = shardService;
    }

    public ResponseEntity<SecretPart> getVersion(String user, String secretName, Long version) {
        validate(user, secretName);
        SecretPart shard = shardService.getShard(key, version);
        return ResponseEntity.ok(shard);
    }

    public ResponseEntity<Map<Long, SecretPart>> getAllVersions(String user, String secretName) {
        validate(user, secretName);
        return ResponseEntity.ok(shardService.getAllVersions(key));
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