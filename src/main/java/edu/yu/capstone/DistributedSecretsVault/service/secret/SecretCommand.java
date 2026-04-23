package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;

public interface SecretCommand<I, O> {
    public ResponseEntity<O> execute(I input);
}
