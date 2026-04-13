package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when a secret with the given key already exists and a create
 * operation is attempted.
 *
 * Maps to HTTP 409 Conflict.
 *
 * @see docs/crud/create.md §4 – Key already persisted on receiving node
 * @see docs/crud/create.md §5 – Key already persisted on another node
 */
public class DuplicateSecretException extends RuntimeException {
    public DuplicateSecretException() {
        super("Secret already exists");
    }

    public DuplicateSecretException(String message) {
        super(message);
    }
}
