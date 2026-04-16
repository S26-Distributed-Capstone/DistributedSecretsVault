package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when a write-lock vote fails because another request already
 * holds the lock for the same {@code user:key}.
 *
 * Maps to HTTP 409 Conflict.
 *
 * @see docs/crud/create.md  §2 – Concurrent create (lock vote failure)
 * @see docs/crud/update.md  §2 – Concurrent updates (lock vote failure)
 */
public class WriteLockConflictException extends RuntimeException {
    public WriteLockConflictException() {
        super("Write lock conflict - another write is in progress for this key");
    }

    public WriteLockConflictException(String message) {
        super(message);
    }
}
