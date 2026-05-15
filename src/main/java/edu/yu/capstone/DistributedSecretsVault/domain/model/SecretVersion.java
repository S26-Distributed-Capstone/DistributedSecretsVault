package edu.yu.capstone.DistributedSecretsVault.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SecretVersion {
    private SecretKey key;
    private long version;
    private long timestampMillis;
}
