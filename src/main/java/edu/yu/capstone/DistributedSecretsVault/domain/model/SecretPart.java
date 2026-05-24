package edu.yu.capstone.DistributedSecretsVault.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single shard (share) of a secret, stored on one node in the cluster.
 * <p>
 * When a secret is created or updated, it is split into {@code N} shards via
 * Shamir's Secret Sharing. Each shard is assigned a unique {@link #partIndex}
 * (1-based) and distributed to a different node. At least {@code K} shards
 * (the threshold) are required to reconstruct the original secret value.
 *
 * @see edu.yu.capstone.DistributedSecretsVault.service.secret.SecretSharingService
 * @see edu.yu.capstone.DistributedSecretsVault.service.secret.SecretReconstructionService
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecretPart {
    /** Composite key identifying the secret this shard belongs to. */
    private SecretKey key;

    /** Version number of the secret (monotonically increasing per secret). */
    private Long version;

    /** 1-based index identifying which share this is in the Shamir split. */
    private int partIndex;

    /** Raw byte payload of the Shamir share. */
    private byte[] shard;
}
