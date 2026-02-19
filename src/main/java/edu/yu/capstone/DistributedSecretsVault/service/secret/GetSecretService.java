package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class GetSecretService implements SecretQuery<String, String>  {

    @Override
    public ResponseEntity<String> execute(String input) {
        // TODO: Implement actual secret retrieval logic
        return ResponseEntity.status(HttpStatus.OK).body("Retrieved secret: " + input);
    }
    
}
