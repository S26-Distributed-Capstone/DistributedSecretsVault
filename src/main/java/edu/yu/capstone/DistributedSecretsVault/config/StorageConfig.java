package edu.yu.capstone.DistributedSecretsVault.config;

import lombok.Data;

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
