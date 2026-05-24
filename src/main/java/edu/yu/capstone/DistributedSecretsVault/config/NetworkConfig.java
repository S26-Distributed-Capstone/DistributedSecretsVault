package edu.yu.capstone.DistributedSecretsVault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * Configuration properties for node identity and network settings.
 * <p>
 * Bound from the {@code network.*} prefix in {@code application.yml}.
 * These settings identify the current node and configure network-level
 * parameters such as multicast discovery and TCP timeouts.
 */
@Validated
@ConfigurationProperties(prefix = "network")
@Data
public class NetworkConfig {
    /** Unique identifier for this node within the cluster. */
    private String nodeId;

    /** Hostname or IP address the node binds to for incoming connections. */
    private String bindHost;

    /** Port the node binds to for incoming connections. */
    private int bindPort;

    /** Multicast group address used for node discovery. */
    private String multicastGroup;

    /** Port used for multicast discovery. */
    private int multicastPort;

    /** Timeout (ms) for TCP connections between nodes. */
    private int tcpTimeoutMillis;
}
