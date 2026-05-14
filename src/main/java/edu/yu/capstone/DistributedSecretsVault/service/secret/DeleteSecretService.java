package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.DeleteSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalDeleteService;

@Service
public class DeleteSecretService implements SecretCommand<DeleteSecretRequest, String> {
    private final InternalDeleteService internalDeleteService;

    public DeleteSecretService(InternalDeleteService internalDeleteService) {
        this.internalDeleteService = internalDeleteService;
    }

    @Override
    public ResponseEntity<String> execute(DeleteSecretRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (input.getUser() == null || input.getUser().isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        SecretKey key = new SecretKey(input.getUser(), input.getDeleteName());
        internalDeleteService.deleteAcrossCluster(key);
        return ResponseEntity.ok("Delete operation completed successfully.");
    }
}
