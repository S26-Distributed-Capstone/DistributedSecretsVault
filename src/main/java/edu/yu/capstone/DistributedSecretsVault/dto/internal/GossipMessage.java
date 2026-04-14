package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import java.util.Map;

import edu.yu.capstone.DistributedSecretsVault.domain.enums.NodeStatus;
import lombok.Data;

@Data
public class GossipMessage {
    private String nodeId;
    private Map<String, NodeStatus> nodeStatuses;
    private long timestampEpochMillis;
}
