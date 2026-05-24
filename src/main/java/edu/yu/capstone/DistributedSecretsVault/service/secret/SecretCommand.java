package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;

/**
 * Generic command contract for secret operations exposed through service classes.
 *
 * @param <I> request type accepted by the command
 * @param <O> response body type produced by the command
 */
public interface SecretCommand<I, O> {
    /**
     * Executes a secret operation and returns the HTTP response expected by the
     * controller layer.
     *
     * @param input operation request
     * @return response entity containing the command result
     */
    public ResponseEntity<O> execute(I input);
}
