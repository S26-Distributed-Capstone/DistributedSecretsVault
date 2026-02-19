package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.dto.secret.PostSecretRequest;

@Service
public class PostSecretService implements SecretCommand<PostSecretRequest, String> {

    @Override
    public ResponseEntity<String> execute(PostSecretRequest input) {
        // TODO: Implement actual secret storage logic
        return ResponseEntity.ok("Secret '" + input.getSecretName() + "' stored successfully with value: " + input.getValue());
    }

    
    
}
