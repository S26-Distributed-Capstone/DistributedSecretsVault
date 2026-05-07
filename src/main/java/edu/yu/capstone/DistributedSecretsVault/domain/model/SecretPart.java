package edu.yu.capstone.DistributedSecretsVault.domain.model;

import lombok.Data;

@Data
public class SecretPart {
    private SecretKey key;
    private long version;
    private int partIndex;
    private byte[] shard;
}
