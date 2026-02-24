package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.dto.secret.PutSecretRequest;

@Service
public class PutSecretService implements SecretCommand<PutSecretRequest, String> {

    @Override
    public ResponseEntity<String> execute(PutSecretRequest input) {
        return ResponseEntity.ok("Secret '" + input.getSecretCurrentName() + "' of old value: "
                + input.getSecretCurrentValue()
                + " replaced with Secret '" + input.getSecretUpdatedName() + "' with value: "
                + input.getSecretUpdatedValue());
    }

}