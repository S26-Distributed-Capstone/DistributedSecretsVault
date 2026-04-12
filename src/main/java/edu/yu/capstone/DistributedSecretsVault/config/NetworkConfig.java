package edu.yu.capstone.DistributedSecretsVault.config;

import lombok.Data;

@Data
public class NetworkConfig {
    private String nodeId;
    private String bindHost;
    private int bindPort;
    private String multicastGroup;
    private int multicastPort;
    private int tcpTimeoutMillis;
}
