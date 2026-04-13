package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when a caller is authenticated but does not have permission to
 * access the requested secret (caller-scoped isolation violation).
 *
 * Maps to HTTP 403 Forbidden.
 *
 * @see docs/crud/retrieve.md §7 – Not Authorized to Access Secret
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException() {
        super("Access denied");
    }

    public AccessDeniedException(String message) {
        super(message);
    }
}
