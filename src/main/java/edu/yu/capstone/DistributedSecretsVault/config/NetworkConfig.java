package edu.yu.capstone.DistributedSecretsVault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

@Validated
@ConfigurationProperties(prefix = "network")
@Data
public class NetworkConfig {
    private String nodeId;
    private String bindHost;
    private int bindPort;
    private String multicastGroup;
    private int multicastPort;
    private int tcpTimeoutMillis;
}
