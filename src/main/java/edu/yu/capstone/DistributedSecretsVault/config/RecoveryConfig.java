package edu.yu.capstone.DistributedSecretsVault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import lombok.Data;

/**
 * Configuration for peer-only cluster recovery.
 * <p>
 * Recovery is always on. These settings only tune startup timing and logging.
 */
@Validated
@ConfigurationProperties(prefix = "cluster.peer-recovery")
@Data
public class RecoveryConfig {

    /**
     * Delay in seconds before starting recovery after node startup.
     * Gives Redis and ScaleCube time to initialize.
     * Default: 2 seconds
     */
    private int delaySeconds = 2;

    /**
     * Maximum time in seconds to wait for peer connectivity during recovery.
     * Default: 15 seconds
     */
    private int peerConnectivityTimeoutSeconds = 15;

    /**
     * Minimum number of peers required for recovery to proceed.
     * Default: 1 (at least one other node in the cluster)
     */
    private int minRequiredPeers = 1;

}
