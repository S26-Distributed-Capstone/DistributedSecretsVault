package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when a node or gateway is unreachable, or the cluster cannot
 * fulfil a request due to insufficient healthy nodes.
 *
 * Maps to HTTP 503 Service Unavailable.
 *
 * @see docs/crud/create.md  §3 – Gateway unable to forward request
 * @see docs/crud/retrieve.md §8 – Gateway Unavailable
 * @see docs/crud/retrieve.md §9 – Node Unavailable
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
