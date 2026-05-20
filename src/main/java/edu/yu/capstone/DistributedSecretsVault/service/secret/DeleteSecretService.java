package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.DeleteSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalDeleteService;

@Service
public class DeleteSecretService implements SecretCommand<DeleteSecretRequest, Void> {
    private static final Logger log = LoggerFactory.getLogger(DeleteSecretService.class);

    private final InternalDeleteService internalDeleteService;

    public DeleteSecretService(InternalDeleteService internalDeleteService) {
        this.internalDeleteService = internalDeleteService;
    }

    @Override
    public ResponseEntity<Void> execute(DeleteSecretRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (input.getUser() == null || input.getUser().isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        SecretKey key = new SecretKey(input.getUser(), input.getDeleteName());
        log.info("Delete secret requested: user={}, secretName={}", key.getOwnerId(), key.getName());
        internalDeleteService.deleteAcrossCluster(key);
        log.info("Delete secret completed: user={}, secretName={}", key.getOwnerId(), key.getName());
        return ResponseEntity.noContent().build();
    }
}
