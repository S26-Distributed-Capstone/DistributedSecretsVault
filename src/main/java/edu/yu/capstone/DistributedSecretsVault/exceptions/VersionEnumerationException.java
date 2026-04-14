package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when a node cannot enumerate the stored versions of a secret
 * due to metadata or storage errors.
 *
 * Maps to HTTP 503 Service Unavailable.
 *
 * @see docs/crud/retrieve.md §11 – Version Enumeration Failure
 */
public class VersionEnumerationException extends ServiceUnavailableException {
    public VersionEnumerationException() {
        super("Failed to enumerate secret versions");
    }

    public VersionEnumerationException(String message) {
        super(message);
    }
}
