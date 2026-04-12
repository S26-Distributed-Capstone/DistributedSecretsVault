package edu.yu.capstone.DistributedSecretsVault.service.cluster;

import java.util.List;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.SecretPartMessage;

public class ClusterManager {
    public boolean tryAcquireWriteLock(SecretKey key, long timestampEpochMillis) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void releaseWriteLock(SecretKey key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public int submitShard(SecretPartMessage message) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<SecretPartMessage> requestShards(SecretKey key, long version, int count) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public int broadcastDelete(SecretKey key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
