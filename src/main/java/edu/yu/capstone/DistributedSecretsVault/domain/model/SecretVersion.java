package edu.yu.capstone.DistributedSecretsVault.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Immutable snapshot representing a successfully committed secret version.
 * <p>
 * Returned by write operations (create, update) as a receipt confirming
 * which version was written and the timestamp at which the write was
 * initiated.
 */
@Data
@AllArgsConstructor
public class SecretVersion {
    /** Composite key identifying the secret. */
    private SecretKey key;

    /** Monotonically increasing version number assigned at write time. */
    private long version;

    /** Wall-clock timestamp (millis since epoch) when the write was initiated. */
    private long timestampMillis;
}
