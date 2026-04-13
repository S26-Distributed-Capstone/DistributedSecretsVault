package edu.yu.capstone.DistributedSecretsVault.exceptions;

/**
 * Thrown when the required quorum of m node confirmations is not reached
 * during either the receive phase or the persist phase of a write operation.
 *
 * Maps to HTTP 503 Service Unavailable.
 *
 * @see docs/crud/create.md  §7 – M nodes do not confirm receiving secret
 * @see docs/crud/create.md  §8 – M nodes do not confirm persisting secret
 * @see docs/crud/update.md  §5 – M nodes do not confirm receiving update
 * @see docs/crud/update.md  §6 – M nodes do not confirm persisting update
 */
public class QuorumNotReachedException extends ServiceUnavailableException {
    public QuorumNotReachedException() {
        super("Not enough confirmations from nodes");
    }

    public QuorumNotReachedException(String message) {
        super(message);
    }
}
