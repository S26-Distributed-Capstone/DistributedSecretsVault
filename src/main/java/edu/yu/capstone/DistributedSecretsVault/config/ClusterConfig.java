package edu.yu.capstone.DistributedSecretsVault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

@Validated
@ConfigurationProperties(prefix = "cluster")
@Data
public class ClusterConfig {
    private int totalNodes;
    private int thresholdK;
    private int quorumM;
    private long lockTimeoutMillis;
    private long writeTimeoutMillis;
}
