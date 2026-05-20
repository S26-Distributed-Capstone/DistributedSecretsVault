package edu.yu.capstone.DistributedSecretsVault.dto.recovery;

/**
 * Recovery inventory entry for a secret shard.
 * Used to advertise which (user:key:version) values exist in the cluster.
 */
public record StateSummary(String ownerId, String keyName, long version, String sourceNodeId) {
    public String toRedisKey() {
        return ownerId + ":" + keyName + ":" + version;
    }
}
