package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when fewer than k shards are available for secret reconstruction,
 * due to node loss, network partition, or individual read failures.
 *
 * Maps to HTTP 503 Service Unavailable.
 *
 * @see docs/crud/retrieve.md §6  – Insufficient Shards
 * @see docs/crud/retrieve.md §10 – Local Shard Read Failure (fallback)
 */
public class InsufficientShardsException extends ServiceUnavailableException {
    public InsufficientShardsException() {
        super("Insufficient shards available to reconstruct secret");
    }

    public InsufficientShardsException(int available, int required) {
        super("Insufficient shards: " + available + " available, " + required + " required");
    }
}
