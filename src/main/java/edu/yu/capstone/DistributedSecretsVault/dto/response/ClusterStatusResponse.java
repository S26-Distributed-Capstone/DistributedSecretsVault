package edu.yu.capstone.DistributedSecretsVault.dto.response;

import lombok.Data;

/**
 * API response body for the {@code /api/v1/cluster/status} endpoint,
 * summarizing the health of the cluster.
 */
@Data
public class ClusterStatusResponse {
    /** Total number of known nodes in the cluster. */
    private int totalNodes;

    /** Number of nodes currently considered healthy. */
    private int healthyNodes;

    /** Number of nodes in a suspect (unreachable) state. */
    private int suspectNodes;

    /** Number of nodes confirmed as failed. */
    private int failedNodes;
}
