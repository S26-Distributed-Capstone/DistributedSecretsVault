package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.dto.secret.DeleteSecretRequest;

@Service
public class DeleteSecretService implements SecretCommand<DeleteSecretRequest, Void> {

    @Override
    public ResponseEntity<Void> execute(DeleteSecretRequest input) {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    
    
}
