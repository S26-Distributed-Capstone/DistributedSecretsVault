package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;

public interface SecretQuery<I, O> {

    ResponseEntity<O> execute(I input);

}
