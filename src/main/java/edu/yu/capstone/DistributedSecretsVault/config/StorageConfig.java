package edu.yu.capstone.DistributedSecretsVault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * Configuration properties for the Redis storage backend.
 * <p>
 * Bound from the {@code storage.*} prefix in {@code application.yml}.
 * Each node connects to its own Redis instance (sidecar pattern) to store
 * its assigned secret shards.
 *
 * @see edu.yu.capstone.DistributedSecretsVault.repository.impl.RedisSecretPartRepository
 */
@Validated
@ConfigurationProperties(prefix = "storage")
@Data
public class StorageConfig {
    /** Hostname of the Redis instance. */
    private String redisHost;

    /** Port of the Redis instance. */
    private int redisPort;

    /** Password for Redis authentication (empty string if unauthenticated). */
    private String redisPassword;
}
