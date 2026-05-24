package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wire-format DTO carrying a single secret shard between nodes during the
 * prepare phase of a write operation (POST, PUT, or REPAIR).
 * <p>
 * This is the serialized form that travels over HTTP; on the receiving side
 * it is converted to a {@link edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart}
 * and buffered in {@link edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecretPartMessage {
    /** Composite key identifying which secret this shard belongs to. */
    private SecretKey key;

    /** Version number of the secret being written. */
    private Long version;

    /** Raw Shamir share bytes. */
    private byte[] shard;

    /** Wall-clock timestamp (ms since epoch) when the write was initiated. */
    private long timestampMillis;

    /** 1-based index of this share in the Shamir split. */
    private int partIndex;
}
