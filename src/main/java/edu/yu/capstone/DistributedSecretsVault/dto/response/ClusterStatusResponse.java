package edu.yu.capstone.DistributedSecretsVault.dto.response;

import lombok.Data;

@Data
public class ClusterStatusResponse {
    private int totalNodes;
    private int healthyNodes;
    private int suspectNodes;
    private int failedNodes;
}
