package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when a request fails authentication — missing, expired, or
 * invalid credentials.
 *
 * Maps to HTTP 401 Unauthorized.
 *
 * @see docs/crud/retrieve.md §7 – Not Authorized (authentication)
 * @see docs/crud/delete.md   §3 – Authentication failure
 */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() {
        super("Authentication failed");
    }

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
