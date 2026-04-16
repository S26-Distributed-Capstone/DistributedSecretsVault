package edu.yu.capstone.DistributedSecretsVault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

@Validated
@ConfigurationProperties(prefix = "storage")
@Data
public class StorageConfig {
    private String redisHost;
    private int redisPort;
    private String redisPassword;
    private String postgresHost;
    private int postgresPort;
    private String postgresUser;
    private String postgresPassword;
}
