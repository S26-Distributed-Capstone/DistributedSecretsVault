package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecretPartMessage {
    private SecretKey key;
    private Long version;
    private byte[] shard;
    private long timestampMillis;
    private int partIndex;
}
