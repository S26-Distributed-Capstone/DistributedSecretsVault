package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when the secret key exists but the requested version does not.
 *
 * Maps to HTTP 404 Not Found.
 *
 * @see docs/crud/retrieve.md §5 – Version Not Found
 */
public class VersionNotFoundException extends RuntimeException {
    public VersionNotFoundException() {
        super("Secret version not found");
    }

    public VersionNotFoundException(String key, int version) {
        super("Version " + version + " not found for secret '" + key + "'");
    }
}
