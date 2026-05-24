package edu.yu.capstone.DistributedSecretsVault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * Configuration properties for cluster topology and distributed operation tuning.
 * <p>
 * Bound from the {@code cluster.*} prefix in {@code application.yml}.
 * These values control how secrets are split, how many nodes must acknowledge
 * a write, and how long pending operations remain valid.
 *
 * @see edu.yu.capstone.DistributedSecretsVault.service.internal.PendingActionsBuffer
 */
@Validated
@ConfigurationProperties(prefix = "cluster")
@Data
public class ClusterConfig {
    /** Total number of nodes in the cluster (N in Shamir's scheme). */
    private int totalNodes;

    /** Minimum number of shards required to reconstruct a secret (K in Shamir's scheme). */
    private int thresholdK;

    /** Minimum number of peer ACKs required to commit a write (quorum M). */
    private int quorumM;

    /** Maximum time (ms) a pending action remains buffered before eviction. */
    private long lockTimeoutMillis;

    /** Maximum time (ms) to wait for all peer responses during a write operation. */
    private long writeTimeoutMillis;

    /** Whether read-repair is enabled (automatically re-split when shard count is low). */
    private boolean repairEnabled = true;

    /** Number of extra shards above the threshold before read-repair triggers. */
    private int repairTriggerBuffer = 1;
}
