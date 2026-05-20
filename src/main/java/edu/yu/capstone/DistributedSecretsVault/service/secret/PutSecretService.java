package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.dto.secret.PutSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretVersion;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalPutService;

@Service
public class PutSecretService implements SecretCommand<PutSecretRequest, String> {
    private static final Logger log = LoggerFactory.getLogger(PutSecretService.class);

    private final InternalPutService internalPutService;

    public PutSecretService(InternalPutService internalPutService) {
        this.internalPutService = internalPutService;
    }

    @Override
    public ResponseEntity<String> execute(PutSecretRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (input.getUser() == null || input.getUser().isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        SecretKey key = new SecretKey(input.getUser(), input.getSecretCurrentName());
        log.info("Update secret requested: user={}, secretName={}", key.getOwnerId(), key.getName());
        SecretVersion version = internalPutService.putAcrossCluster(key, input.getSecretUpdatedValue());
        log.info("Update secret completed: user={}, secretName={}, version={}",
                key.getOwnerId(), key.getName(), version.getVersion());
        return ResponseEntity.ok("Secret updated (version: " + version.getVersion() + ")");
    }
}
