package edu.yu.capstone.DistributedSecretsVault.domain.model;

import lombok.Data;

@Data
public class SecretVersion {
    private SecretKey key;
    private long version;
    private long timestampMillis;
}