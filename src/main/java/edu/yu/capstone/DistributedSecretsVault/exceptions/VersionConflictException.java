package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when a write operation discovers that the version being written
 * has already been superseded (e.g. a concurrent update incremented the
 * version number before the current write could commit).
 *
 * Maps to HTTP 409 Conflict.
 */
public class VersionConflictException extends RuntimeException {
    public VersionConflictException() {
        super("Version conflict detected");
    }

    public VersionConflictException(String message) {
        super(message);
    }
}
