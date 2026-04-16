package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when no secret exists for the given key and the requested
 * operation requires it (retrieve, update, or delete).
 *
 * Maps to HTTP 404 Not Found.
 *
 * @see docs/crud/retrieve.md §4 – Secret Not Found
 * @see docs/crud/delete.md   §2 – Secret not found
 */
public class SecretNotFoundException extends RuntimeException {
    public SecretNotFoundException() {
        super("Secret not found");
    }

    public SecretNotFoundException(String message) {
        super(message);
    }
}
