package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import lombok.Data;

@Data
public class SecretPartMessage {
    private SecretKey key;
    private long version;
    private int partIndex;
    private byte[] shard;
    private long timestampEpochMillis;
}
