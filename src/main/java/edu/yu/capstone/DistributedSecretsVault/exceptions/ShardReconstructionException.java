package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when collected shards fail integrity checks or Shamir reconstruction
 * cannot complete despite having enough shards.
 *
 * Maps to HTTP 500 Internal Server Error.
 *
 * @see docs/crud/retrieve.md §12 – Shard Reconstruction Failure
 */
public class ShardReconstructionException extends RuntimeException {
    public ShardReconstructionException() {
        super("Shard reconstruction failed - integrity check failure");
    }

    public ShardReconstructionException(String message) {
        super(message);
    }
}
