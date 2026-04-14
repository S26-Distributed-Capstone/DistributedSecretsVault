package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when the gateway-attached timestamp metadata is missing or invalid
 * on a write request, preventing write coordination from starting.
 *
 * Maps to HTTP 503 Service Unavailable.
 *
 * @see docs/crud/create.md  §6 – Gateway metadata missing or invalid
 * @see docs/crud/update.md  §4 – Gateway metadata missing or invalid
 */
public class InvalidRequestMetadataException extends ServiceUnavailableException {
    public InvalidRequestMetadataException() {
        super("Invalid request metadata");
    }

    public InvalidRequestMetadataException(String message) {
        super(message);
    }
}
