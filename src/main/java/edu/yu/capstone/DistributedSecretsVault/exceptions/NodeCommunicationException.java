package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when an HTTP request to a peer node fails due to a network error,
 * timeout, or unexpected response.
 *
 * Maps to HTTP 503 Service Unavailable.
 *
 * @see docs/crud/retrieve.md §9 – Node Unavailable
 */
public class NodeCommunicationException extends ServiceUnavailableException {
    public NodeCommunicationException() {
        super("Node communication failure");
    }

    public NodeCommunicationException(String message) {
        super(message);
    }
}
