package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import lombok.Data;

@Data
public class HeartbeatMessage {
    private String nodeId;
    private long timestampEpochMillis;
}
