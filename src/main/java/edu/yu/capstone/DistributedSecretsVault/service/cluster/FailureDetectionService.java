package edu.yu.capstone.DistributedSecretsVault.service.cluster;

import java.util.Map;

import edu.yu.capstone.DistributedSecretsVault.domain.enums.NodeStatus;

public class FailureDetectionService {
    public NodeStatus evaluateNode(String nodeId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Map<String, NodeStatus> snapshot() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
