package edu.yu.capstone.DistributedSecretsVault.config;

import lombok.Data;

@Data
public class ClusterConfig {
    private int totalNodes;
    private int thresholdK;
    private int quorumM;
    private long lockTimeoutMillis;
    private long writeTimeoutMillis;
}
