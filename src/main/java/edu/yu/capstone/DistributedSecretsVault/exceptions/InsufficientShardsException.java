package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when fewer than k shards (secret parts) are available for
 * reconstruction due to node loss, network partition, or read failures.
 *
 * Maps to HTTP 503 Service Unavailable.
 *
 * @see docs/crud/retrieve.md §6  – Insufficient Shards
 * @see docs/crud/retrieve.md §10 – Local Shard Read Failure (fallback)
 */
public class InsufficientShardsException extends ServiceUnavailableException {
    public InsufficientShardsException() {
        super("Insufficient secret parts to reconstruct");
    }

    public InsufficientShardsException(String message) {
        super(message);
    }

    public InsufficientShardsException(int available, int required) {
        super("Insufficient shards: " + available + " available, " + required + " required");
    }
}
