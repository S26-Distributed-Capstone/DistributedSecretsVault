package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when an internal cluster operation cannot be applied because the
 * receiving node's local coordination state does not match the request.
 *
 * Maps to HTTP 409 Conflict.
 */
public class InternalOperationConflictException extends RuntimeException {
    public InternalOperationConflictException(String message) {
        super(message);
    }
}
